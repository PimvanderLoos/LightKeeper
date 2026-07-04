package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        when(process.isAlive()).thenReturn(true, false);
        when(process.waitFor(5_000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        setField(serverProcess, "process", process);
        setField(serverProcess, "outputThread", outputThread);

        // execute
        serverProcess.kill();

        // verify
        verify(process).destroyForcibly();
        verify(process).waitFor(5_000L, TimeUnit.MILLISECONDS);
        assertThat(getField(serverProcess, "process")).isNull();
        assertThat(getField(serverProcess, "outputThread")).isNull();
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
        final java.lang.reflect.Method appendOutputLine =
            MinecraftServerProcess.class.getDeclaredMethod("appendOutputLine", String.class);
        appendOutputLine.setAccessible(true);
        appendOutputLine.invoke(serverProcess, "line one");
        appendOutputLine.invoke(serverProcess, "line two");

        // execute
        final List<String> lines = serverProcess.snapshotOutputLines();

        // verify
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

        final java.lang.reflect.Method appendOutputLine =
            MinecraftServerProcess.class.getDeclaredMethod("appendOutputLine", String.class);
        appendOutputLine.setAccessible(true);
        appendOutputLine.invoke(serverProcess, "survivor line");

        // execute
        final List<String> lines = serverProcess.snapshotOutputLines();

        // verify
        assertThat(lines).hasSize(2);
        assertThat(lines.getFirst()).contains("Discarded").contains("5");
        assertThat(lines.get(1)).isEqualTo("survivor line");
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
