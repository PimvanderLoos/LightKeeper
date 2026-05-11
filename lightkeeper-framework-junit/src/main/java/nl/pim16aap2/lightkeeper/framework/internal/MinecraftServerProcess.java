package nl.pim16aap2.lightkeeper.framework.internal;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
final class MinecraftServerProcess
{
    private static final System.Logger LOG = System.getLogger(MinecraftServerProcess.class.getName());

    private static final int MAX_CAPTURED_OUTPUT_LINES = 10_000;
    private static final Duration SESSION_LOCK_RELEASE_TIMEOUT = Duration.ofSeconds(10);

    private final RuntimeManifest runtimeManifest;
    private final Path diagnosticsDirectory;
    private final Object outputLinesLock = new Object();
    @GuardedBy("outputLinesLock")
    private final ArrayDeque<String> outputLines = new ArrayDeque<>(MAX_CAPTURED_OUTPUT_LINES);
    @GuardedBy("outputLinesLock")
    private long discardedOutputLineCount = 0L;
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
        if (isRunning())
            throw new IllegalStateException("Minecraft server is already running.");

        clearStoppedProcessState();
        if (outputThread != null && outputThread.isAlive())
            throw new IllegalStateException("Previous Minecraft server output reader is still stopping.");
        waitForWorldSessionLockRelease(SESSION_LOCK_RELEASE_TIMEOUT);

        final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        final ProcessBuilder processBuilder = getProcessBuilder(javaExecutable);
        final CountDownLatch startLatch = new CountDownLatch(1);

        try
        {
            final Process startedProcess = processBuilder.start();
            final Thread startedOutputThread = createOutputReaderThread(startedProcess, startLatch);
            process = startedProcess;
            outputThread = startedOutputThread;
            startedOutputThread.start();
            awaitStartupOrFail(startedProcess, startLatch, timeout);
        }
        catch (Exception exception)
        {
            writeDiagnostics("startup-failure");
            stop(Duration.ofSeconds(5));
            throw new IllegalStateException("Failed to start Minecraft server.", exception);
        }
    }

    boolean isRunning()
    {
        return process != null && process.isAlive();
    }

    private void awaitStartupOrFail(Process runningProcess, CountDownLatch startLatch, Duration timeout)
        throws InterruptedException
    {
        final long startupDeadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < startupDeadlineNanos)
        {
            if (startLatch.await(200L, TimeUnit.MILLISECONDS))
                return;

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

    void kill()
    {
        final Process currentProcess = process;
        if (currentProcess == null)
            return;

        try
        {
            if (currentProcess.isAlive())
            {
                forceProcessExit(currentProcess, "Minecraft server process did not exit after forced kill.");
            }
        }
        finally
        {
            joinOutputThread(Duration.ofSeconds(5));
            waitForWorldSessionLockRelease(SESSION_LOCK_RELEASE_TIMEOUT);
            clearProcessState(currentProcess);
        }
    }

    void stop(Duration timeout)
    {
        final Process currentProcess = process;
        if (currentProcess == null)
            return;

        try
        {
            if (currentProcess.isAlive())
            {
                try (
                    BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(currentProcess.getOutputStream(), StandardCharsets.UTF_8)))
                {
                    writer.write("stop");
                    writer.newLine();
                    writer.flush();
                }

                final boolean stopped = currentProcess.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!stopped && currentProcess.isAlive())
                    forceProcessExit(currentProcess, "Minecraft server process did not exit after forced stop.");
            }
        }
        catch (Exception exception)
        {
            if (currentProcess.isAlive())
            {
                currentProcess.destroyForcibly();
                waitForProcessExit(currentProcess, Duration.ofSeconds(5));
            }
            writeDiagnostics("shutdown-failure");
        }
        finally
        {
            joinOutputThread(Duration.ofSeconds(5));
            waitForWorldSessionLockRelease(SESSION_LOCK_RELEASE_TIMEOUT);
            clearProcessState(currentProcess);
        }
    }

    private static void forceProcessExit(Process process, String failureMessage)
    {
        process.destroyForcibly();
        if (!waitForProcessExit(process, Duration.ofSeconds(5)) && process.isAlive())
            throw new IllegalStateException(failureMessage);
    }

    private static boolean waitForProcessExit(Process process, Duration timeout)
    {
        try
        {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void joinOutputThread(Duration timeout)
    {
        if (outputThread != null)
        {
            try
            {
                outputThread.join(timeout.toMillis());
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
            if (!outputThread.isAlive())
                outputThread = null;
        }
    }

    private void clearProcessState(Process completedProcess)
    {
        if (Objects.equals(process, completedProcess) && !completedProcess.isAlive())
            process = null;
        if (outputThread != null && !outputThread.isAlive())
            outputThread = null;
    }

    private void clearStoppedProcessState()
    {
        final Process currentProcess = process;
        if (currentProcess != null && !currentProcess.isAlive())
        {
            joinOutputThread(Duration.ofSeconds(5));
            clearProcessState(currentProcess);
        }
    }

    private void waitForWorldSessionLockRelease(Duration timeout)
    {
        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline))
        {
            if (isWorldSessionLockAvailable(serverDirectory))
                return;

            try
            {
                TimeUnit.MILLISECONDS.sleep(100L);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (!isWorldSessionLockAvailable(serverDirectory))
            throw new IllegalStateException(
                "Minecraft world session lock was not released within %d ms: %s"
                    .formatted(timeout.toMillis(), serverDirectory.resolve("world/session.lock"))
            );
    }

    static boolean isWorldSessionLockAvailable(Path serverDirectory)
    {
        final Path sessionLock = serverDirectory.resolve("world/session.lock");
        if (!Files.exists(sessionLock))
            return true;

        try (
            FileChannel channel = FileChannel.open(sessionLock, StandardOpenOption.WRITE);
            FileLock ignored = channel.tryLock())
        {
            return ignored != null;
        }
        catch (IOException | OverlappingFileLockException exception)
        {
            return false;
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
                Integer.toString(runtimeManifest.runtimeProtocolVersion()), StandardCharsets.UTF_8);
            Files.writeString(
                bundleDirectory.resolve("server-output.log"),
                String.join(System.lineSeparator(), snapshotOutputLines()),
                StandardCharsets.UTF_8
            );
        }
        catch (IOException exception)
        {
            LOG.log(System.Logger.Level.TRACE, () -> "Failed to write diagnostics bundle: " + exception.getMessage());
        }
    }

    List<String> snapshotOutputLines()
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

    private Thread createOutputReaderThread(Process process, CountDownLatch startLatch)
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
                            startLatch.countDown();
                    }
                }
                catch (IOException exception)
                {
                    LOG.log(
                        System.Logger.Level.TRACE,
                        () -> "Minecraft output reader stopped: " + exception.getMessage()
                    );
                }
            });
    }
}
