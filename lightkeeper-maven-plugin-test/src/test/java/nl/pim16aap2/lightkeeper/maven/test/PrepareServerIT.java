package nl.pim16aap2.lightkeeper.maven.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;

class PrepareServerIT
{
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration LOG_WAIT_TIMEOUT = Duration.ofSeconds(30);
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
        assertThat(runtimeManifest.runtimeProtocolVersion()).isEqualTo("v1");
        assertThat(runtimeManifest.agentCacheIdentity()).isNotBlank();

        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Path serverJar = Path.of(runtimeManifest.serverJar());
        final Path udsSocketPath = Path.of(runtimeManifest.udsSocketPath());
        final Path agentJarPath = Path.of(Objects.requireNonNull(runtimeManifest.agentJar()));
        final Path installedAgentJarPath = serverDirectory.resolve("plugins").resolve(agentJarPath.getFileName());

        assertThat(serverDirectory).isDirectory();
        assertThat(serverJar).isRegularFile();
        assertThat(serverDirectory.resolve("eula.txt")).isRegularFile();
        assertThat(udsSocketPath.getParent()).isDirectory();
        assertThat(agentJarPath).isRegularFile();
        assertThat(installedAgentJarPath).isRegularFile();
        assertThat(runtimeManifest.agentJarSha256()).hasSize(64);
        assertThat(sha256(installedAgentJarPath)).isEqualTo(runtimeManifest.agentJarSha256());
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
        final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        final ServerHandle serverHandle = ServerHandle.start(
            Path.of(runtimeManifest.serverDirectory()),
            Path.of(runtimeManifest.serverJar()),
            javaExecutable
        );

        // execute
        final boolean started = serverHandle.awaitStartup(STARTUP_TIMEOUT);
        final boolean stopped = serverHandle.stop(STOP_TIMEOUT);
        final int exitCode = serverHandle.exitCode();

        // verify
        assertThat(started)
            .withFailMessage("Server did not start within timeout. Output tail:%n%s", serverHandle.outputTail())
            .isTrue();
        assertThat(stopped)
            .withFailMessage("Server did not stop within timeout. Output tail:%n%s", serverHandle.outputTail())
            .isTrue();
        assertThat(exitCode)
            .withFailMessage("Server exited with non-zero code. Output tail:%n%s", serverHandle.outputTail())
            .isZero();
    }

    @Test
    void preparedServer_shouldExecuteCommandsAgainstRunningServer()
        throws Exception
    {
        // setup
        final RuntimeManifest runtimeManifest = OBJECT_MAPPER.readValue(
            getRuntimeManifestPath().toFile(),
            RuntimeManifest.class
        );
        final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        final String marker = "LightKeeperCommandMarker";
        final ServerHandle serverHandle = ServerHandle.start(
            Path.of(runtimeManifest.serverDirectory()),
            Path.of(runtimeManifest.serverJar()),
            javaExecutable
        );

        // execute
        final boolean started = serverHandle.awaitStartup(STARTUP_TIMEOUT);
        serverHandle.sendCommand("say " + marker);
        final boolean sawCommandOutput = serverHandle.awaitLogLine(line -> line.contains(marker), LOG_WAIT_TIMEOUT);
        final boolean stopped = serverHandle.stop(STOP_TIMEOUT);

        // verify
        assertThat(started)
            .withFailMessage("Server did not start within timeout. Output tail:%n%s", serverHandle.outputTail())
            .isTrue();
        assertThat(sawCommandOutput)
            .withFailMessage("Did not observe command output in server logs. Output tail:%n%s", serverHandle.outputTail())
            .isTrue();
        assertThat(stopped)
            .withFailMessage("Server did not stop within timeout. Output tail:%n%s", serverHandle.outputTail())
            .isTrue();
        assertThat(serverHandle.exitCode())
            .withFailMessage("Server exited with non-zero code. Output tail:%n%s", serverHandle.outputTail())
            .isZero();
    }

    private static final class ServerHandle
    {
        private final Process process;
        private final PrintWriter commandWriter;
        private final List<String> outputLines = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch startedLatch = new CountDownLatch(1);
        private final Thread outputThread;

        private ServerHandle(Process process)
        {
            this.process = process;
            this.commandWriter = new PrintWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8),
                true
            );
            this.outputThread = createOutputThread(process, outputLines, startedLatch);
        }

        static ServerHandle start(Path serverDirectory, Path serverJar, Path javaExecutable)
            throws IOException
        {
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

            final ServerHandle serverHandle = new ServerHandle(process);
            serverHandle.outputThread.start();
            return serverHandle;
        }

        private static Thread createOutputThread(
            Process process,
            List<String> outputLines,
            CountDownLatch startedLatch)
        {
            return new Thread(
                () -> {
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
                        if (process.isAlive())
                            throw new RuntimeException("Failed to read server output", exception);
                    }
                },
                "lightkeeper-paper-output-reader"
            );
        }

        boolean awaitStartup(Duration timeout)
            throws InterruptedException
        {
            return startedLatch.await(timeout.toSeconds(), TimeUnit.SECONDS);
        }

        void sendCommand(String command)
        {
            commandWriter.println(command);
            commandWriter.flush();
        }

        boolean awaitLogLine(Predicate<String> predicate, Duration timeout)
            throws InterruptedException
        {
            final long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline)
            {
                synchronized (outputLines)
                {
                    for (String line : outputLines)
                    {
                        if (predicate.test(line))
                            return true;
                    }
                }
                Thread.sleep(50L);
            }
            return false;
        }

        boolean stop(Duration timeout)
            throws Exception
        {
            sendCommand("stop");
            final boolean stopped = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!stopped && process.isAlive())
                process.destroyForcibly();
            commandWriter.close();
            outputThread.join(TimeUnit.SECONDS.toMillis(5));
            return stopped;
        }

        int exitCode()
        {
            return process.isAlive() ? -1 : process.exitValue();
        }

        String outputTail()
        {
            if (outputLines.isEmpty())
                return "<no output>";

            synchronized (outputLines)
            {
                final int startIndex = Math.max(0, outputLines.size() - 40);
                return String.join(System.lineSeparator(), outputLines.subList(startIndex, outputLines.size()));
            }
        }
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

    private record RuntimeManifest(
        String serverType,
        String serverVersion,
        long paperBuildId,
        String cacheKey,
        String serverDirectory,
        String serverJar,
        String udsSocketPath,
        String agentAuthToken,
        String agentJar,
        String agentJarSha256,
        String runtimeProtocolVersion,
        String agentCacheIdentity
    )
    {
    }

    private static String sha256(Path path)
        throws IOException
    {
        final MessageDigest messageDigest;
        try
        {
            messageDigest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }

        try (InputStream inputStream = Files.newInputStream(path))
        {
            final byte[] buffer = new byte[8_192];
            int read;
            while ((read = inputStream.read(buffer)) != -1)
                messageDigest.update(buffer, 0, read);
        }

        return HexFormat.of().formatHex(messageDigest.digest());
    }
}
