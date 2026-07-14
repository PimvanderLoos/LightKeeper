package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class MinecraftServerProcessTest
{
    @Test
    void kill_shouldWaitForForcedProcessExitAndClearStoppedState(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            tempDirectory.resolve("diagnostics")
        );
        final Process process = mock();
        final Thread outputThread = Thread.ofPlatform().unstarted(() -> { });
        outputThread.start();
        outputThread.join();
        final Thread errorThread = Thread.ofPlatform().unstarted(() -> { });
        errorThread.start();
        errorThread.join();
        when(process.isAlive()).thenReturn(true, false);
        when(process.waitFor(5_000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        setField(serverProcess, "process", process);
        setField(serverProcess, "outputThread", outputThread);
        setField(serverProcess, "errorThread", errorThread);

        // execute
        serverProcess.kill();

        // verify — both reader threads are joined and cleared, not just the output one
        verify(process).destroyForcibly();
        verify(process).waitFor(5_000L, TimeUnit.MILLISECONDS);
        assertThat(getField(serverProcess, "process")).isNull();
        assertThat(getField(serverProcess, "outputThread")).isNull();
        assertThat(getField(serverProcess, "errorThread")).isNull();
    }

    @Test
    void stop_shouldForceKillAndThrowWhenProcessSurvivesShutdownFailure(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path diagnosticsDirectory = tempDirectory.resolve("diagnostics");
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            diagnosticsDirectory
        );
        final Process process = mock();
        // Trigger the shutdown-failure catch: writing the "stop" command fails, and the process never dies.
        when(process.isAlive()).thenReturn(true);
        when(process.getOutputStream()).thenThrow(new RuntimeException("stream unavailable"));
        when(process.waitFor(5_000L, TimeUnit.MILLISECONDS)).thenReturn(false);
        setField(serverProcess, "process", process);

        // execute + verify — a surviving process must fail loudly rather than leave a zombie
        assertThatThrownBy(() -> serverProcess.stop(Duration.ofSeconds(1)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("still alive after a forced kill");
        verify(process).destroyForcibly();

        // and the diagnostics bundle must include the original failure's stack trace
        try (Stream<Path> bundleFiles = Files.walk(diagnosticsDirectory))
        {
            final List<Path> failureFiles = bundleFiles
                .filter(path -> path.getFileName().toString().equals("failure.txt"))
                .toList();
            assertThat(failureFiles).hasSize(1);
            assertThat(Files.readString(failureFiles.getFirst())).contains("stream unavailable");
        }
    }

    @Test
    void start_shouldRejectLingeringOutputReaderBeforeNewProcessLaunch(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            tempDirectory.resolve("diagnostics")
        );
        final Thread lingeringOutputThread = Thread.ofPlatform().unstarted(() ->
        {
            try
            {
                Thread.sleep(Duration.ofSeconds(30));
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
        });
        lingeringOutputThread.start();
        setField(serverProcess, "outputThread", lingeringOutputThread);

        // execute + verify
        try
        {
            assertThatThrownBy(() -> serverProcess.start(Duration.ofMillis(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("output reader is still stopping");
        }
        finally
        {
            lingeringOutputThread.interrupt();
            lingeringOutputThread.join(Duration.ofSeconds(5));
        }
    }

    @Test
    void start_shouldRejectLingeringErrorReaderBeforeNewProcessLaunch(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup — the output reader is absent/finished, only the stderr reader is still lingering
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            tempDirectory.resolve("diagnostics")
        );
        final Thread lingeringErrorThread = Thread.ofPlatform().unstarted(() ->
        {
            try
            {
                Thread.sleep(Duration.ofSeconds(30));
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
        });
        lingeringErrorThread.start();
        setField(serverProcess, "errorThread", lingeringErrorThread);

        // execute + verify
        try
        {
            assertThatThrownBy(() -> serverProcess.start(Duration.ofMillis(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("output reader is still stopping");
        }
        finally
        {
            lingeringErrorThread.interrupt();
            lingeringErrorThread.join(Duration.ofSeconds(5));
        }
    }

    @Test
    void getProcessBuilder_shouldNotRedirectErrorStreamIntoStdout(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup — stdout and stderr must stay on separate pipes so OutputLine provenance is meaningful
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            tempDirectory.resolve("diagnostics")
        );
        final Method getProcessBuilder =
            MinecraftServerProcess.class.getDeclaredMethod("getProcessBuilder", Path.class);
        getProcessBuilder.setAccessible(true);

        // execute
        final ProcessBuilder processBuilder =
            (ProcessBuilder) getProcessBuilder.invoke(serverProcess, Path.of("java"));

        // verify
        assertThat(processBuilder.redirectErrorStream()).isFalse();
    }

    @Test
    void createOutputReaderThread_shouldCaptureLinesAndSignalStartupOnReadyMarker(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            tempDirectory.resolve("diagnostics")
        );
        final Process process = mock();
        final String content = "Starting server" + System.lineSeparator()
            + "Done (1.234s)! For help, type \"help\"" + System.lineSeparator();
        when(process.getInputStream())
            .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        final CountDownLatch startLatch = new CountDownLatch(1);
        final Method createOutputReaderThread = MinecraftServerProcess.class
            .getDeclaredMethod("createOutputReaderThread", Process.class, CountDownLatch.class);
        createOutputReaderThread.setAccessible(true);
        final Thread readerThread = (Thread) createOutputReaderThread.invoke(serverProcess, process, startLatch);

        // execute
        readerThread.start();
        readerThread.join(Duration.ofSeconds(5).toMillis());

        // verify — the readiness marker line signals startup, and both lines land on the stdout pipe
        assertThat(startLatch.getCount()).isZero();
        assertThat(serverProcess.snapshotOutputLines())
            .containsExactly("Starting server", "Done (1.234s)! For help, type \"help\"");
        assertThat(serverProcess.snapshotStderrLinesFrom(0L)).isEmpty();
    }

    @Test
    void createErrorReaderThread_shouldCaptureLinesWithStderrProvenance(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            tempDirectory.resolve("diagnostics")
        );
        final Process process = mock();
        final String content = "java.lang.RuntimeException: raw" + System.lineSeparator();
        when(process.getErrorStream())
            .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        final Method createErrorReaderThread =
            MinecraftServerProcess.class.getDeclaredMethod("createErrorReaderThread", Process.class);
        createErrorReaderThread.setAccessible(true);
        final Thread readerThread = (Thread) createErrorReaderThread.invoke(serverProcess, process);

        // execute
        readerThread.start();
        readerThread.join(Duration.ofSeconds(5).toMillis());

        // verify — the line is captured with stderr provenance, distinct from the stdout pipe
        assertThat(serverProcess.snapshotStderrLinesFrom(0L))
            .singleElement()
            .satisfies(line ->
            {
                assertThat(line.text()).isEqualTo("java.lang.RuntimeException: raw");
                assertThat(line.fromStderr()).isTrue();
            });
    }

    @Test
    void isWorldSessionLockAvailable_shouldReturnFalseWhenLockIsHeld(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path sessionLock = tempDirectory.resolve("world/session.lock");
        Files.createDirectories(sessionLock.getParent());
        Files.createFile(sessionLock);

        // execute + verify
        try (
            FileChannel channel = FileChannel.open(sessionLock, StandardOpenOption.WRITE);
            FileLock ignored = channel.lock())
        {
            assertThat(MinecraftServerProcess.isWorldSessionLockAvailable(tempDirectory)).isFalse();
        }
        assertThat(MinecraftServerProcess.isWorldSessionLockAvailable(tempDirectory)).isTrue();
    }

    @Test
    void isWorldSessionLockAvailable_shouldReturnTrueWhenSessionLockFileDoesNotExist(@TempDir Path tempDirectory)
    {
        // setup - no session.lock file created

        // execute
        final boolean available = MinecraftServerProcess.isWorldSessionLockAvailable(tempDirectory);

        // verify
        assertThat(available).isTrue();
    }

    @Test
    void isWorldSessionLockAvailable_shouldReturnFalseWhenNonDefaultWorldLockIsHeld(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup - only the nether world lock exists and is held; the default 'world' lock is absent
        final Path netherLock = tempDirectory.resolve("world_nether/session.lock");
        Files.createDirectories(netherLock.getParent());
        Files.createFile(netherLock);

        // execute + verify
        try (
            FileChannel channel = FileChannel.open(netherLock, StandardOpenOption.WRITE);
            FileLock ignored = channel.lock())
        {
            assertThat(MinecraftServerProcess.isWorldSessionLockAvailable(tempDirectory)).isFalse();
        }
        assertThat(MinecraftServerProcess.isWorldSessionLockAvailable(tempDirectory)).isTrue();
    }

    @Test
    void appendExtraJvmArgs_shouldKeepQuotedValueContainingSpacesAsSingleToken()
        throws Exception
    {
        // setup
        final java.lang.reflect.Method appendExtraJvmArgs =
            MinecraftServerProcess.class.getDeclaredMethod("appendExtraJvmArgs", List.class, String.class);
        appendExtraJvmArgs.setAccessible(true);
        final List<String> command = new java.util.ArrayList<>();

        // execute
        appendExtraJvmArgs.invoke(null, command, "-Xmx1G -Dfoo=\"hello world\" -Dbar=baz");

        // verify
        assertThat(command).containsExactly("-Xmx1G", "-Dfoo=hello world", "-Dbar=baz");
    }

    @Test
    void snapshotOutputLines_shouldReturnCapturedLinesInOrder(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            tempDirectory.resolve("diagnostics")
        );
        appendOutputLine(serverProcess, "line one", false);
        appendOutputLine(serverProcess, "line two", true);

        // execute
        final List<String> lines = serverProcess.snapshotOutputLines();

        // verify — snapshotOutputLines merges both pipes in arrival order
        assertThat(lines).containsExactly("line one", "line two");
    }

    @Test
    void snapshotOutputLines_shouldPrependDiscardNoteWhenLinesWereEvicted(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            tempDirectory.resolve("diagnostics")
        );
        final java.lang.reflect.Field discardedField =
            MinecraftServerProcess.class.getDeclaredField("discardedOutputLineCount");
        discardedField.setAccessible(true);
        discardedField.set(serverProcess, 5L);

        appendOutputLine(serverProcess, "survivor line", false);

        // execute
        final List<String> lines = serverProcess.snapshotOutputLines();

        // verify
        assertThat(lines).hasSize(2);
        assertThat(lines.getFirst()).contains("Discarded").contains("5");
        assertThat(lines.get(1)).isEqualTo("survivor line");
    }

    @Test
    void totalOutputLineCount_shouldIncludeDiscardedLines(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            tempDirectory.resolve("diagnostics")
        );
        final java.lang.reflect.Field discardedField =
            MinecraftServerProcess.class.getDeclaredField("discardedOutputLineCount");
        discardedField.setAccessible(true);
        discardedField.set(serverProcess, 7L);
        appendOutputLine(serverProcess, "line one", false);
        appendOutputLine(serverProcess, "line two", true);

        // execute
        final long totalLineCount = serverProcess.totalOutputLineCount();

        // verify
        assertThat(totalLineCount).isEqualTo(9L);
    }

    @Test
    void snapshotStderrLinesFrom_shouldFilterByProvenanceAndWatermark(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final MinecraftServerProcess serverProcess = new MinecraftServerProcess(
            runtimeManifest(tempDirectory),
            tempDirectory.resolve("diagnostics")
        );
        appendOutputLine(serverProcess, "stdout before", false);
        appendOutputLine(serverProcess, "stderr before", true);
        final long watermark = serverProcess.totalOutputLineCount();
        appendOutputLine(serverProcess, "stdout after", false);
        appendOutputLine(serverProcess, "stderr after", true);

        // execute
        final List<MinecraftServerProcess.OutputLine> stderrLines =
            serverProcess.snapshotStderrLinesFrom(watermark);

        // verify — only stderr lines at or past the watermark are returned
        assertThat(stderrLines)
            .singleElement()
            .satisfies(line ->
            {
                assertThat(line.text()).isEqualTo("stderr after");
                assertThat(line.fromStderr()).isTrue();
                assertThat(line.timestampMillis()).isPositive();
            });
    }

    private static void appendOutputLine(MinecraftServerProcess serverProcess, String line, boolean fromStderr)
        throws ReflectiveOperationException
    {
        final java.lang.reflect.Method appendOutputLine =
            MinecraftServerProcess.class.getDeclaredMethod("appendOutputLine", String.class, boolean.class);
        appendOutputLine.setAccessible(true);
        appendOutputLine.invoke(serverProcess, line, fromStderr);
    }

    private static RuntimeManifest runtimeManifest(Path tempDirectory)
    {
        return new RuntimeManifest(
            "PAPER",
            "1.21.11",
            1L,
            "cache-key",
            tempDirectory.toString(),
            tempDirectory.resolve("server.jar").toString(),
            512,
            tempDirectory.resolve("agent.sock").toString(),
            "token",
            null,
            null,
            RuntimeProtocol.VERSION,
            "agent-cache",
            null,
            List.of()
        );
    }

    private static void setField(Object target, String fieldName, Object value)
        throws ReflectiveOperationException
    {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName)
        throws ReflectiveOperationException
    {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
