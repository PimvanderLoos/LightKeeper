package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class BuildOutputLeaseTest
{
    @Test
    void acquire_shouldPublishLiveOwnershipAndKeepTheLockInodeAfterClose(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path lockPath = BuildOutputLease.lockPath(root);
        final Path ownerPath = lockPath.resolveSibling("owner.json");
        final BuildOutputLease lease = BuildOutputLease.acquire(root, Duration.ofSeconds(1));

        // execute
        final BuildLeaseIdentity identity = GeneratedMetadata.read(ownerPath, BuildLeaseIdentity.class);
        lease.close();
        lease.close();

        // verify
        assertThat(identity.process().processId()).isEqualTo(ProcessHandle.current().pid());
        assertThat(identity.process().matchesCurrentAncestry()).isTrue();
        assertThat(lockPath).isRegularFile();
        assertThat(ownerPath).doesNotExist();
        try (var next = BuildOutputLease.acquire(root, Duration.ofSeconds(1)))
        {
            assertThat(next.identity().nonce()).isNotEqualTo(identity.nonce());
        }
    }

    @Test
    void acquire_shouldTimeOutWithoutDisturbingTheExistingLease(@TempDir Path root)
        throws Exception
    {
        // setup
        try (var held = BuildOutputLease.acquire(root, Duration.ofSeconds(1)))
        {
            // execute
            final var failure = catchThrowable(() -> BuildOutputLease.acquire(root, Duration.ofMillis(50)));
            final var owner = GeneratedMetadata.read(
                BuildOutputLease.lockPath(root).resolveSibling("owner.json"),
                BuildLeaseIdentity.class
            );

            // verify
            assertThat(failure).isInstanceOf(IllegalStateException.class).hasRootCauseMessage(
                "Timed out waiting for the JVM's producer build lease: " + BuildOutputLease.lockPath(root)
            );
            assertThat(owner).isEqualTo(held.identity());
        }
    }

    @Test
    void acquire_shouldRetainInterruptionAndReleaseFailedAcquisition(@TempDir Path root)
        throws Exception
    {
        // setup
        final AtomicBoolean interrupted = new AtomicBoolean();
        final AtomicBoolean failed = new AtomicBoolean();
        final Thread waiter = Thread.ofPlatform().unstarted(() ->
        {
            try (var unexpected = BuildOutputLease.acquire(root, Duration.ofSeconds(5)))
            {
                failed.set(false);
            }
            catch (IllegalStateException exception)
            {
                failed.set(true);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });

        // execute
        try (var held = BuildOutputLease.acquire(root, Duration.ofSeconds(1)))
        {
            waiter.start();
            waiter.interrupt();
            waiter.join(2_000);

            // verify
            assertThat(waiter.isAlive()).isFalse();
            assertThat(failed).isTrue();
            assertThat(interrupted).isTrue();
            assertThat(held.identity().process().matchesCurrentAncestry()).isTrue();
        }
    }

    @Test
    void acquire_shouldKeepTheKernelLeaseAfterAnotherThreadTimesOut(@TempDir Path root)
        throws Exception
    {
        // setup
        try (var held = BuildOutputLease.acquire(root, Duration.ofSeconds(1)))
        {
            // execute
            final var failure = catchThrowable(() -> BuildOutputLease.acquire(root, Duration.ofMillis(20)));
            final String externalProbe = runProbe(root);

            // verify
            assertThat(failure).isInstanceOf(IllegalStateException.class);
            assertThat(externalProbe).isEqualTo("BUSY");
            assertThat(held.identity().process().matchesCurrentAncestry()).isTrue();
        }
    }

    @Test
    void acquire_shouldRejectInvalidTimeoutBeforeCreatingMetadata(@TempDir Path root)
    {
        // setup
        final Duration timeout = Duration.ZERO;

        // execute
        final var failure = catchThrowable(() -> BuildOutputLease.acquire(root, timeout));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThat(Files.exists(root.resolve(".lightkeeper"))).isFalse();
    }
    private static String runProbe(Path root)
        throws IOException, InterruptedException
    {
        final ProcessBuilder builder = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-cp",
            System.getProperty("java.class.path"),
            Probe.class.getName(),
            root.toString()
        );
        builder.redirectErrorStream(true);
        final Process child = builder.start();
        try
        {
            assertThat(child.waitFor(5, TimeUnit.SECONDS)).isTrue();
            final String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            assertThat(child.exitValue()).as(output).isZero();
            return output;
        }
        finally
        {
            if (child.isAlive())
            {
                child.destroyForcibly().waitFor();
            }
        }
    }

    /** Child-JVM probe for real process ancestry and kernel lease behavior. */
    public static final class Probe
    {
        private Probe()
        {
        }

        /**
         * Executes one child process lease check.
         *
         * @param args
         *     Reactor directory to probe.
         */
        public static void main(String[] args)
        {
            final Path root = Path.of(args[0]);
            try (var acquired = BuildOutputLease.acquire(root, Duration.ofMillis(100)))
            {
                System.out.println("ACQUIRED");
            }
            catch (IllegalStateException exception)
            {
                System.out.println("BUSY");
            }
        }
    }

}
