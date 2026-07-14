package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.internal.OrphanReaper.WatchedProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OrphanReaperTest
{
    private static final Duration TEST_POLL_INTERVAL = Duration.ofMillis(10);

    @Test
    void run_shouldReturnWithoutKillingWhenServerAlreadyExited()
    {
        // setup
        final ProcessHandle watchedJvm = mock();
        final ProcessHandle serverProcess = mock();
        when(serverProcess.isAlive()).thenReturn(false);

        // execute
        OrphanReaper.run(anyIdentity(watchedJvm), anyIdentity(serverProcess), TEST_POLL_INTERVAL);

        // verify
        verify(serverProcess, never()).destroyForcibly();
    }

    @Test
    void run_shouldKillServerTreeWhenWatchedJvmIsDead()
    {
        // setup
        final ProcessHandle watchedJvm = mock();
        final ProcessHandle serverProcess = mock();
        final ProcessHandle serverChild = mock();
        when(watchedJvm.isAlive()).thenReturn(false);
        when(serverProcess.isAlive()).thenReturn(true);
        when(serverProcess.descendants()).thenReturn(Stream.of(serverChild));

        // execute
        OrphanReaper.run(anyIdentity(watchedJvm), anyIdentity(serverProcess), TEST_POLL_INTERVAL);

        // verify
        verify(serverChild).destroyForcibly();
        verify(serverProcess).destroyForcibly();
    }

    @Test
    void run_shouldKillServerTreeWhenWatchedJvmIsNull()
    {
        // setup
        final ProcessHandle serverProcess = mock();
        when(serverProcess.isAlive()).thenReturn(true);
        when(serverProcess.descendants()).thenReturn(Stream.empty());

        // execute
        OrphanReaper.run(null, anyIdentity(serverProcess), TEST_POLL_INTERVAL);

        // verify
        verify(serverProcess).destroyForcibly();
    }

    @Test
    void run_shouldKeepWaitingWhileBothProcessesAreAlive()
    {
        // setup
        final ProcessHandle watchedJvm = mock();
        final ProcessHandle serverProcess = mock();
        when(serverProcess.isAlive()).thenReturn(true, true, true);
        when(watchedJvm.isAlive()).thenReturn(true, true, false);
        when(serverProcess.descendants()).thenReturn(Stream.empty());

        // execute
        OrphanReaper.run(anyIdentity(watchedJvm), anyIdentity(serverProcess), TEST_POLL_INTERVAL);

        // verify
        verify(serverProcess).destroyForcibly();
    }

    @Test
    void run_shouldKillServerWhenWatchedJvmPidWasReused()
    {
        // setup
        // The watched PID is alive but belongs to a different (recycled) process: treat as dead and reap.
        final ProcessHandle watchedJvm = aliveHandleWithStartMillis(9_999L);
        final ProcessHandle serverProcess = mock();
        when(serverProcess.isAlive()).thenReturn(true);
        when(serverProcess.descendants()).thenReturn(Stream.empty());

        // execute
        OrphanReaper.run(
            new WatchedProcess(watchedJvm, 1_234L),
            anyIdentity(serverProcess),
            TEST_POLL_INTERVAL
        );

        // verify
        verify(serverProcess).destroyForcibly();
    }

    @Test
    void run_shouldNotKillWhenServerPidWasReused()
    {
        // setup
        // The server PID is alive but belongs to a different (recycled) process: never kill a stranger.
        final ProcessHandle serverProcess = aliveHandleWithStartMillis(9_999L);

        // execute
        OrphanReaper.run(null, new WatchedProcess(serverProcess, 1_234L), TEST_POLL_INTERVAL);

        // verify
        verify(serverProcess, never()).destroyForcibly();
    }

    @Test
    void isAliveAndSame_shouldAcceptMatchingStartInstant()
    {
        // setup
        final ProcessHandle handle = aliveHandleWithStartMillis(1_234L);

        // execute + verify
        assertThat(new WatchedProcess(handle, 1_234L).isAliveAndSame()).isTrue();
    }

    @Test
    void killProcessTree_shouldDestroyDescendantsAndRoot()
    {
        // setup
        final ProcessHandle root = mock();
        final ProcessHandle childA = mock();
        final ProcessHandle childB = mock();
        when(root.descendants()).thenReturn(Stream.of(childA, childB));

        // execute
        OrphanReaper.killProcessTree(root);

        // verify
        verify(childA).destroyForcibly();
        verify(childB).destroyForcibly();
        verify(root).destroyForcibly();
    }

    @Test
    @Timeout(30)
    void launch_shouldReapServerProcessWhenWatchedJvmDies()
        throws Exception
    {
        // setup
        // Real end-to-end: a short-lived JVM plays the test JVM, a long-lived JVM plays the server.
        final Process fakeTestJvm = startHelperJvm(ShortLivedMain.class);
        final Process fakeServer = startHelperJvm(LongLivedMain.class);

        try
        {
            // execute
            final Process reaper = OrphanReaperLauncher.launch(fakeTestJvm.pid(), fakeServer.pid());

            // verify
            assertThat(reaper).isNotNull();
            assertThat(fakeTestJvm.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("the fake test JVM should exit on its own")
                .isTrue();
            assertThat(fakeServer.waitFor(15, java.util.concurrent.TimeUnit.SECONDS))
                .as("the reaper should kill the fake server once the fake test JVM died")
                .isTrue();
        }
        finally
        {
            fakeTestJvm.destroyForcibly();
            fakeServer.destroyForcibly();
        }
    }

    @Test
    @Timeout(30)
    void launch_shouldLeaveServerAloneWhileWatchedJvmIsAlive()
        throws Exception
    {
        // setup
        // This JVM plays the watched test JVM, so the server must not be reaped.
        final Process fakeServer = startHelperJvm(LongLivedMain.class);

        try
        {
            // execute
            final Process reaper = OrphanReaperLauncher.launch(ProcessHandle.current().pid(), fakeServer.pid());

            // verify
            assertThat(reaper).isNotNull();
            Thread.sleep(2_000);
            assertThat(fakeServer.isAlive())
                .as("the server must not be reaped while the watched JVM is alive")
                .isTrue();
        }
        finally
        {
            fakeServer.destroyForcibly();
        }
    }

    @Test
    @Timeout(30)
    void launch_shouldDetachReaperIntoItsOwnProcessGroup()
        throws Exception
    {
        // setup
        // Whole-group kills (e.g. GNU timeout in CI) must not take the reaper down with the test JVM.
        org.junit.jupiter.api.Assumptions.assumeTrue(
            java.nio.file.Files.isExecutable(Path.of("/usr/bin/setsid")),
            "setsid not available; session detachment is best-effort on this platform");
        final Process fakeServer = startHelperJvm(LongLivedMain.class);

        try
        {
            // execute
            final Process reaper = OrphanReaperLauncher.launch(ProcessHandle.current().pid(), fakeServer.pid());

            // verify
            assertThat(reaper).isNotNull();
            final long ownProcessGroup = readProcessGroup(ProcessHandle.current().pid());
            final long reaperProcessGroup = readProcessGroup(reaper.pid());
            assertThat(reaperProcessGroup)
                .as("the reaper must not share the test JVM's process group")
                .isNotEqualTo(ownProcessGroup);
        }
        finally
        {
            fakeServer.destroyForcibly();
        }
    }

    /** Reads field 5 (pgrp) of /proc/[pid]/stat; only called when setsid exists, i.e. on Linux-like systems. */
    private static long readProcessGroup(long pid)
        throws Exception
    {
        final String stat = java.nio.file.Files.readString(Path.of("/proc/" + pid + "/stat"));
        // The comm field (2) is parenthesized and may contain spaces; parse from after the closing paren.
        // pgrp is the third space-separated token after the comm field.
        final String afterComm = stat.substring(stat.lastIndexOf(')') + 2);
        int start = 0;
        for (int skippedTokens = 0; skippedTokens < 2; ++skippedTokens)
            start = afterComm.indexOf(' ', start) + 1;
        return Long.parseLong(afterComm.substring(start, afterComm.indexOf(' ', start)));
    }

    @Test
    void run_shouldReturnAndRestoreInterruptFlagWhenInterrupted()
        throws Exception
    {
        // setup
        final ProcessHandle watchedJvm = mock();
        final ProcessHandle serverProcess = mock();
        when(serverProcess.isAlive()).thenReturn(true);
        when(watchedJvm.isAlive()).thenReturn(true);
        final java.util.concurrent.atomic.AtomicBoolean interruptRestored =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        final Thread reaperThread = Thread.ofPlatform().unstarted(() ->
        {
            OrphanReaper.run(anyIdentity(watchedJvm), anyIdentity(serverProcess), Duration.ofSeconds(30));
            interruptRestored.set(Thread.currentThread().isInterrupted());
        });

        // execute
        reaperThread.start();
        Thread.sleep(100);
        reaperThread.interrupt();
        reaperThread.join(5_000);

        // verify
        assertThat(reaperThread.isAlive()).isFalse();
        assertThat(interruptRestored).isTrue();
        verify(serverProcess, never()).destroyForcibly();
    }

    @Test
    void isAliveAndSame_shouldAcceptWhenActualStartInstantIsUnavailable()
    {
        // setup
        final ProcessHandle handle = mock();
        final ProcessHandle.Info info = mock();
        when(handle.isAlive()).thenReturn(true);
        when(handle.info()).thenReturn(info);
        when(info.startInstant()).thenReturn(Optional.empty());

        // execute + verify
        assertThat(new WatchedProcess(handle, 1_234L).isAliveAndSame()).isTrue();
    }

    @Test
    @Timeout(30)
    void runFromArgs_shouldReturnImmediatelyWhenServerProcessDoesNotExist()
        throws Exception
    {
        // setup
        final long freedPid = exitedProcessPid();

        // execute + verify — returns without looping because there is nothing to guard
        OrphanReaper.runFromArgs(new String[]{
            Long.toString(ProcessHandle.current().pid()),
            Long.toString(OrphanReaper.START_INSTANT_UNKNOWN),
            Long.toString(freedPid),
            Long.toString(OrphanReaper.START_INSTANT_UNKNOWN),
        });
    }

    @Test
    @Timeout(30)
    void runFromArgs_shouldWatchUntilServerExits()
        throws Exception
    {
        // setup
        final Process fakeServer = startHelperJvm(ShortLivedMain.class);

        try
        {
            // execute — blocks while the short-lived server is alive, then returns on its own
            OrphanReaper.runFromArgs(new String[]{
                Long.toString(ProcessHandle.current().pid()),
                Long.toString(WatchedProcess.startEpochMillis(ProcessHandle.current())),
                Long.toString(fakeServer.pid()),
                Long.toString(WatchedProcess.startEpochMillis(fakeServer.toHandle())),
            });

            // verify
            assertThat(fakeServer.isAlive()).isFalse();
        }
        finally
        {
            fakeServer.destroyForcibly();
        }
    }

    @Test
    @Timeout(30)
    void main_shouldExitWithCodeTwoWhenArgumentCountIsWrong()
        throws Exception
    {
        // setup
        final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        final Path mainClasses =
            Path.of(OrphanReaper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final ProcessBuilder processBuilder = new ProcessBuilder(
            javaExecutable.toString(),
            "-cp",
            mainClasses.toString(),
            OrphanReaper.class.getName(),
            "only-one-argument"
        );
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

        // execute
        final Process reaper = processBuilder.start();

        // verify
        assertThat(reaper.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(reaper.exitValue()).isEqualTo(2);
    }

    @Test
    @Timeout(30)
    void launch_shouldStillLaunchWhenServerPidDoesNotExist()
        throws Exception
    {
        // setup
        final long freedPid = exitedProcessPid();

        // execute
        final Process reaper = OrphanReaperLauncher.launch(ProcessHandle.current().pid(), freedPid);

        // verify — the reaper starts and exits on its own since there is nothing to guard
        assertThat(reaper).isNotNull();
        assertThat(reaper.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }

    /** Returns the PID of a process that has already exited, i.e. a PID with no live process behind it. */
    private static long exitedProcessPid()
        throws Exception
    {
        final Process shortLived = startHelperJvm(ShortLivedMain.class);
        shortLived.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        return shortLived.pid();
    }

    private static WatchedProcess anyIdentity(ProcessHandle handle)
    {
        return new WatchedProcess(handle, OrphanReaper.START_INSTANT_UNKNOWN);
    }

    private static ProcessHandle aliveHandleWithStartMillis(long startEpochMillis)
    {
        final ProcessHandle handle = mock();
        final ProcessHandle.Info info = mock();
        when(handle.isAlive()).thenReturn(true);
        when(handle.info()).thenReturn(info);
        when(info.startInstant()).thenReturn(Optional.of(Instant.ofEpochMilli(startEpochMillis)));
        return handle;
    }

    private static Process startHelperJvm(Class<?> mainClass)
        throws Exception
    {
        final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        final Path testClasses =
            Path.of(OrphanReaperTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final ProcessBuilder processBuilder = new ProcessBuilder(
            javaExecutable.toString(),
            "-Xms16m",
            "-Xmx32m",
            "-cp",
            testClasses.toString(),
            mainClass.getName()
        );
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        return processBuilder.start();
    }

    /** Helper main that exits quickly, simulating a test JVM that died. */
    public static final class ShortLivedMain
    {
        public static void main(String[] args)
            throws InterruptedException
        {
            Thread.sleep(500);
        }
    }

    /** Helper main that lives long enough to require reaping, simulating a leaked server. */
    public static final class LongLivedMain
    {
        public static void main(String[] args)
            throws InterruptedException
        {
            Thread.sleep(120_000);
        }
    }
}
