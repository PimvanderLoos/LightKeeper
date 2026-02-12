package nl.pim16aap2.lightkeeper.maven.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.maven.RuntimeManifest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PrepareServerIT
{
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(45);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void prepareServer_shouldWriteRuntimeManifestWithExistingPaths()
        throws IOException
    {
        // setup
        final Path runtimeManifestPath = getRuntimeManifestPath();

        // execute
        final RuntimeManifest runtimeManifest = OBJECT_MAPPER.readValue(runtimeManifestPath.toFile(), RuntimeManifest.class);

        // verify
        assertThat(runtimeManifest.serverType()).isEqualTo("paper");
        assertThat(runtimeManifest.serverVersion()).isNotBlank();
        assertThat(runtimeManifest.paperBuildId()).isPositive();
        assertThat(runtimeManifest.cacheKey()).hasSize(64);
        assertThat(runtimeManifest.agentAuthToken()).isNotBlank();

        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Path serverJar = Path.of(runtimeManifest.serverJar());
        final Path udsSocketPath = Path.of(runtimeManifest.udsSocketPath());

        assertThat(serverDirectory).isDirectory();
        assertThat(serverJar).isRegularFile();
        assertThat(serverDirectory.resolve("eula.txt")).isRegularFile();
        assertThat(udsSocketPath.getParent()).isDirectory();
        assertThat(runtimeManifest.agentJar()).isNull();
        assertThat(runtimeManifest.agentJarSha256()).isNull();
    }

    @Test
    void preparedServer_shouldStartAndStopSuccessfully()
        throws Exception
    {
        // setup
        final RuntimeManifest runtimeManifest = OBJECT_MAPPER.readValue(
            getRuntimeManifestPath().toFile(),
            RuntimeManifest.class
        );
        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Path serverJar = Path.of(runtimeManifest.serverJar());
        final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        final List<String> outputLines = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch startedLatch = new CountDownLatch(1);
        final Process process = new ProcessBuilder(
            javaExecutable.toString(),
            "-Xmx1024M",
            "-Xms1024M",
            "-jar",
            serverJar.toString(),
            "--nogui")
            .directory(serverDirectory.toFile())
            .redirectErrorStream(true)
            .start();
        final Thread outputThread = createOutputThread(process, outputLines, startedLatch);

        // execute
        final boolean started = startedLatch.await(STARTUP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        try (PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true))
        {
            writer.println("stop");
            writer.flush();
        }
        final boolean stopped = process.waitFor(STOP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        final int exitCode = stopped ? process.exitValue() : -1;

        if (!stopped && process.isAlive())
            process.destroyForcibly();

        outputThread.join(TimeUnit.SECONDS.toMillis(5));

        // verify
        assertThat(started)
            .withFailMessage("Server did not start within timeout. Output tail:%n%s", outputTail(outputLines))
            .isTrue();
        assertThat(stopped)
            .withFailMessage("Server did not stop within timeout. Output tail:%n%s", outputTail(outputLines))
            .isTrue();
        assertThat(exitCode)
            .withFailMessage("Server exited with non-zero code. Output tail:%n%s", outputTail(outputLines))
            .isZero();
    }

    private static Thread createOutputThread(Process process, List<String> outputLines, CountDownLatch startedLatch)
    {
        final Thread outputThread = new Thread(() -> {
            try (
                BufferedReader reader =
                    new BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    outputLines.add(line);
                    if (line.contains("Done (") && line.endsWith(")! For help, type \"help\""))
                        startedLatch.countDown();
                }
            }
            catch (IOException exception)
            {
                throw new RuntimeException("Failed to read server output", exception);
            }
        }, "lightkeeper-paper-output-reader");
        outputThread.setDaemon(true);
        outputThread.start();
        return outputThread;
    }

    private static Path getRuntimeManifestPath()
    {
        final String runtimeManifestPathValue = System.getProperty("lightkeeper.runtimeManifestPath");
        assertThat(runtimeManifestPathValue)
            .withFailMessage("System property 'lightkeeper.runtimeManifestPath' was not set.")
            .isNotBlank();

        final Path runtimeManifestPath = Path.of(runtimeManifestPathValue);
        assertThat(runtimeManifestPath)
            .withFailMessage("Runtime manifest '%s' does not exist.", runtimeManifestPath)
            .isRegularFile();
        return runtimeManifestPath;
    }

    private static String outputTail(List<String> outputLines)
    {
        if (outputLines.isEmpty())
            return "<no output>";

        final int startIndex = Math.max(0, outputLines.size() - 40);
        return String.join(System.lineSeparator(), outputLines.subList(startIndex, outputLines.size()));
    }
}
