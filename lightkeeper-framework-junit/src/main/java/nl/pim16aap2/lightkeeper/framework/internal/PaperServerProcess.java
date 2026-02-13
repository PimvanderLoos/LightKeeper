package nl.pim16aap2.lightkeeper.framework.internal;

import lombok.extern.java.Log;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Paper server process lifecycle.
 */
@Log
final class PaperServerProcess
{
    private final RuntimeManifest runtimeManifest;
    private final Path diagnosticsDirectory;
    private final List<String> outputLines = new ArrayList<>();
    private final CountDownLatch startedLatch = new CountDownLatch(1);
    private @Nullable Process process;
    private @Nullable Thread outputThread;

    PaperServerProcess(RuntimeManifest runtimeManifest, Path diagnosticsDirectory)
    {
        this.runtimeManifest = Objects.requireNonNull(runtimeManifest, "runtimeManifest may not be null.");
        this.diagnosticsDirectory =
            Objects.requireNonNull(diagnosticsDirectory, "diagnosticsDirectory may not be null.");
    }

    void start(Duration timeout)
    {
        final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Path serverJar = Path.of(runtimeManifest.serverJar());

        final ProcessBuilder processBuilder = new ProcessBuilder(
            javaExecutable.toString(),
            "-Xmx1024M",
            "-Xms1024M",
            "-D" + RuntimeProtocol.PROPERTY_SOCKET_PATH + "=" + runtimeManifest.udsSocketPath(),
            "-D" + RuntimeProtocol.PROPERTY_AUTH_TOKEN + "=" + runtimeManifest.agentAuthToken(),
            "-D" + RuntimeProtocol.PROPERTY_PROTOCOL_VERSION + "=" + runtimeManifest.runtimeProtocolVersion(),
            "-D" + RuntimeProtocol.PROPERTY_EXPECTED_AGENT_SHA256 + "=" +
                Objects.requireNonNullElse(runtimeManifest.agentJarSha256(), ""),
            "-jar",
            serverJar.toString(),
            "--nogui"
        );
        processBuilder.directory(serverDirectory.toFile());
        processBuilder.redirectErrorStream(true);

        try
        {
            process = processBuilder.start();
            outputThread = createOutputReaderThread(process, outputLines, startedLatch);
            outputThread.start();
            final boolean started = startedLatch.await(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!started)
            {
                writeDiagnostics("startup-timeout");
                stop(Duration.ofSeconds(5));
                throw new IllegalStateException(
                    "Paper server did not start within timeout. Tail:%n%s".formatted(outputTail())
                );
            }
        }
        catch (Exception exception)
        {
            writeDiagnostics("startup-failure");
            stop(Duration.ofSeconds(5));
            throw new IllegalStateException("Failed to start Paper server.", exception);
        }
    }

    void stop(Duration timeout)
    {
        if (process == null)
            return;

        try
        {
            if (process.isAlive())
            {
                try (
                    BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)))
                {
                    writer.write("stop");
                    writer.newLine();
                    writer.flush();
                }

                final boolean stopped = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
                if (!stopped && process.isAlive())
                    process.destroyForcibly();
            }
        }
        catch (Exception exception)
        {
            if (process.isAlive())
                process.destroyForcibly();
            writeDiagnostics("shutdown-failure");
        }

        if (outputThread != null)
        {
            try
            {
                outputThread.join(TimeUnit.SECONDS.toMillis(5));
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String outputTail()
    {
        synchronized (outputLines)
        {
            final int startIndex = Math.max(0, outputLines.size() - 40);
            return String.join(System.lineSeparator(), outputLines.subList(startIndex, outputLines.size()));
        }
    }

    private void writeDiagnostics(String reason)
    {
        try
        {
            Files.createDirectories(diagnosticsDirectory);
            final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-");
            final Path bundleDirectory = diagnosticsDirectory.resolve("bundle-" + timestamp);
            Files.createDirectories(bundleDirectory);

            Files.writeString(bundleDirectory.resolve("reason.txt"), reason, StandardCharsets.UTF_8);
            Files.writeString(bundleDirectory.resolve("manifest-server-dir.txt"),
                runtimeManifest.serverDirectory(), StandardCharsets.UTF_8);
            Files.writeString(bundleDirectory.resolve("manifest-socket-path.txt"),
                runtimeManifest.udsSocketPath(), StandardCharsets.UTF_8);
            Files.writeString(bundleDirectory.resolve("manifest-protocol-version.txt"),
                runtimeManifest.runtimeProtocolVersion(), StandardCharsets.UTF_8);
            Files.writeString(bundleDirectory.resolve("server-output.log"),
                String.join(System.lineSeparator(), outputLines), StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            log.fine(() -> "Failed to write diagnostics bundle: " + exception.getMessage());
        }
    }

    private static Thread createOutputReaderThread(
        Process process,
        List<String> outputLines,
        CountDownLatch startedLatch)
    {
        return Thread.ofPlatform()
            .name("lightkeeper-paper-output-reader")
            .daemon(true)
            .unstarted(() ->
            {
                try (
                    BufferedReader reader =
                        new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        synchronized (outputLines)
                        {
                            outputLines.add(line);
                        }

                        if (line.contains("Done (") && line.endsWith(")! For help, type \"help\""))
                            startedLatch.countDown();
                    }
                }
                catch (IOException exception)
                {
                    log.fine(() -> "Paper output reader stopped: " + exception.getMessage());
                }
            });
    }
}
