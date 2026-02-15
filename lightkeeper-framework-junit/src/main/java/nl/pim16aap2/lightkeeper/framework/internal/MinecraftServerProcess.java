package nl.pim16aap2.lightkeeper.framework.internal;

import com.google.errorprone.annotations.concurrent.GuardedBy;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Minecraft server process lifecycle.
 */
@Log
final class MinecraftServerProcess
{
    private static final int MAX_CAPTURED_OUTPUT_LINES = 10_000;

    private final RuntimeManifest runtimeManifest;
    private final Path diagnosticsDirectory;
    private final Object outputLinesLock = new Object();
    @GuardedBy("outputLinesLock")
    private final ArrayDeque<String> outputLines = new ArrayDeque<>(MAX_CAPTURED_OUTPUT_LINES);
    @GuardedBy("outputLinesLock")
    private long discardedOutputLineCount = 0L;
    private final CountDownLatch startedLatch = new CountDownLatch(1);
    private @Nullable Process process;
    private @Nullable Thread outputThread;

    MinecraftServerProcess(RuntimeManifest runtimeManifest, Path diagnosticsDirectory)
    {
        this.runtimeManifest = Objects.requireNonNull(runtimeManifest, "runtimeManifest may not be null.");
        this.diagnosticsDirectory =
            Objects.requireNonNull(diagnosticsDirectory, "diagnosticsDirectory may not be null.");
    }

    void start(Duration timeout)
    {
        final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        final ProcessBuilder processBuilder = getProcessBuilder(javaExecutable);

        try
        {
            process = processBuilder.start();
            outputThread = createOutputReaderThread(process);
            outputThread.start();
            awaitStartupOrFail(timeout);
        }
        catch (Exception exception)
        {
            writeDiagnostics("startup-failure");
            stop(Duration.ofSeconds(5));
            throw new IllegalStateException("Failed to start Minecraft server.", exception);
        }
    }

    private void awaitStartupOrFail(Duration timeout)
        throws InterruptedException
    {
        final long startupDeadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < startupDeadlineNanos)
        {
            if (startedLatch.await(200L, TimeUnit.MILLISECONDS))
                return;

            final Process runningProcess = Objects.requireNonNull(process, "process may not be null.");
            if (!runningProcess.isAlive())
            {
                throw new IllegalStateException(
                    "Minecraft server exited before readiness marker. Exit code: %d. Tail:%n%s"
                        .formatted(runningProcess.exitValue(), outputTail())
                );
            }
        }

        throw new IllegalStateException(
            "Minecraft server did not start within timeout. Tail:%n%s".formatted(outputTail())
        );
    }

    private ProcessBuilder getProcessBuilder(Path javaExecutable)
    {
        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Path serverJar = Path.of(runtimeManifest.serverJar());
        final int memoryMb = runtimeManifest.memoryMb();
        final List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add("-Xmx" + memoryMb + "M");
        command.add("-Xms" + memoryMb + "M");
        appendExtraJvmArgs(command, runtimeManifest.extraJvmArgs());
        command.add("-D" + RuntimeProtocol.PROPERTY_SOCKET_PATH + "=" + runtimeManifest.udsSocketPath());
        command.add("-D" + RuntimeProtocol.PROPERTY_AUTH_TOKEN + "=" + runtimeManifest.agentAuthToken());
        command.add("-D" + RuntimeProtocol.PROPERTY_PROTOCOL_VERSION + "=" + runtimeManifest.runtimeProtocolVersion());
        command.add("-D" + RuntimeProtocol.PROPERTY_EXPECTED_AGENT_SHA256 + "=" +
            Objects.requireNonNullElse(runtimeManifest.agentJarSha256(), ""));
        command.add("-DIReallyKnowWhatIAmDoingISwear=true");
        command.add("-jar");
        command.add(serverJar.toString());
        command.add("--nogui");
        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(serverDirectory.toFile());
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    private static void appendExtraJvmArgs(List<String> command, @Nullable String extraJvmArgs)
    {
        if (extraJvmArgs == null || extraJvmArgs.isBlank())
            return;
        command.addAll(Arrays.asList(extraJvmArgs.split("\\s+")));
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
        final List<String> outputLinesSnapshot = snapshotOutputLines();
        final int startIndex = Math.max(0, outputLinesSnapshot.size() - 40);
        return String.join(
            System.lineSeparator(),
            outputLinesSnapshot.subList(startIndex, outputLinesSnapshot.size())
        );
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
            Files.writeString(
                bundleDirectory.resolve("server-output.log"),
                String.join(System.lineSeparator(), snapshotOutputLines()),
                StandardCharsets.UTF_8
            );
        }
        catch (IOException exception)
        {
            log.fine(() -> "Failed to write diagnostics bundle: " + exception.getMessage());
        }
    }

    private List<String> snapshotOutputLines()
    {
        synchronized (outputLinesLock)
        {
            if (discardedOutputLineCount == 0L)
                return List.copyOf(outputLines);

            final List<String> snapshot = new ArrayList<>(outputLines.size() + 1);
            snapshot.add(
                "[lightkeeper] Discarded %d older server log lines before this captured tail."
                    .formatted(discardedOutputLineCount)
            );
            snapshot.addAll(outputLines);
            return List.copyOf(snapshot);
        }
    }

    private void appendOutputLine(String line)
    {
        synchronized (outputLinesLock)
        {
            if (outputLines.size() == MAX_CAPTURED_OUTPUT_LINES)
            {
                outputLines.removeFirst();
                discardedOutputLineCount++;
            }
            outputLines.addLast(line);
        }
    }

    private Thread createOutputReaderThread(Process process)
    {
        return Thread.ofPlatform()
            .name("lightkeeper-minecraft-output-reader")
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
                        appendOutputLine(line);

                        if (line.contains("Done (") && line.endsWith(")! For help, type \"help\""))
                            startedLatch.countDown();
                    }
                }
                catch (IOException exception)
                {
                    log.fine(() -> "Minecraft output reader stopped: " + exception.getMessage());
                }
            });
    }
}
