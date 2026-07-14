package nl.pim16aap2.lightkeeper.framework.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.time.Duration;
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
        OrphanReaper.run(watchedJvm, serverProcess, TEST_POLL_INTERVAL);

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
        OrphanReaper.run(watchedJvm, serverProcess, TEST_POLL_INTERVAL);

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
        OrphanReaper.run(null, serverProcess, TEST_POLL_INTERVAL);

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
        OrphanReaper.run(watchedJvm, serverProcess, TEST_POLL_INTERVAL);

        // verify
        verify(serverProcess).destroyForcibly();
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
            assertThat(reaper.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("the reaper should exit once the server is gone")
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

            // Once the server dies on its own, the reaper should follow.
            fakeServer.destroyForcibly();
            assertThat(reaper.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("the reaper should exit once the server is gone")
                .isTrue();
        }
        finally
        {
            fakeServer.destroyForcibly();
        }
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
