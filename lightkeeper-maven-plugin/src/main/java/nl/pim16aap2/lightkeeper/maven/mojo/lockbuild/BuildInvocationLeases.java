package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import nl.pim16aap2.lightkeeper.runtime.BuildLeaseIdentity;
import nl.pim16aap2.lightkeeper.runtime.BuildOutputLease;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shares one early build lease across the modules of a live Maven invocation.
 * Snapshot export releases it before Failsafe; a later module's initialize guard acquires a new lease.
 */
public final class BuildInvocationLeases
{
    /** Invocation entries live only while their producer-output lease is held. */
    private static final ConcurrentHashMap<MavenSession, InvocationLease> LEASES = new ConcurrentHashMap<>();
    /** Shutdown-only diagnostics when a failed build could not release its lease normally. */
    private static final System.Logger LOG = System.getLogger(BuildInvocationLeases.class.getName());

    static
    {
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform()
            .name("lightkeeper-build-lease-release")
            .unstarted(BuildInvocationLeases::releaseRemaining));
    }

    private BuildInvocationLeases()
    {
    }

    /**
     * Acquires or reuses the lease protecting the current Maven reactor's producer outputs.
     *
     * @param session
     *     Live Maven invocation.
     * @param timeout
     *     Maximum wait for a competing invocation.
     * @param log
     *     Maven lifecycle log.
     * @return Lease identity, excluding any ownership transfer to the caller.
     */
    public static BuildLeaseIdentity acquire(MavenSession session, Duration timeout, Log log)
    {
        return LEASES.computeIfAbsent(session, ignored -> acquireNew(session, timeout, log)).identity();
    }

    /**
     * Releases an invocation's outputs after snapshot capture, including skipped preparation.
     *
     * @param session
     *     Maven invocation, or {@code null} when no invocation was attached to the caller.
     */
    public static void release(@Nullable MavenSession session)
    {
        if (session == null)
        {
            return;
        }
        final InvocationLease lease = LEASES.remove(session);
        if (lease != null && lease.ownedLease() != null)
        {
            lease.ownedLease().close();
        }
    }

    /**
     * Releases outputs after preparation fails while preserving the original failure.
     *
     * @param session
     *     Maven invocation, when available.
     * @param failure
     *     Failure that remains authoritative if lease cleanup also fails.
     */
    public static void releaseAfterFailure(@Nullable MavenSession session, Throwable failure)
    {
        try
        {
            release(session);
        }
        catch (RuntimeException cleanupFailure)
        {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static InvocationLease acquireNew(MavenSession session, Duration timeout, Log log)
    {
        final Path root = Path.of(Objects.requireNonNull(
            session.getExecutionRootDirectory(),
            "Maven did not provide its reactor root directory."
        ));
        final String borrowedNonce = System.getenv(BuildOutputLease.BORROW_NONCE_ENV);
        if (borrowedNonce != null && !borrowedNonce.isBlank())
        {
            final BuildLeaseIdentity identity = BuildOutputLease.verifyBorrowed(root, borrowedNonce);
            log.info("LK_BUILD_LOCK: Using the ancestor build lease for '" + root + "'.");
            return new InvocationLease(null, identity);
        }
        log.info("LK_BUILD_LOCK: Waiting for producer outputs in '" + root + "'.");
        final BuildOutputLease lease = BuildOutputLease.acquire(root, timeout);
        log.info("LK_BUILD_LOCK: Acquired producer outputs in '" + root + "'.");
        return new InvocationLease(lease, lease.identity());
    }

    private static void releaseRemaining()
    {
        LEASES.values().forEach(lease ->
        {
            if (lease.ownedLease() != null)
            {
                try
                {
                    lease.ownedLease().close();
                }
                catch (RuntimeException exception)
                {
                    LOG.log(
                        System.Logger.Level.WARNING,
                        "Failed to release a LightKeeper build lease at shutdown.",
                        exception
                    );
                }
            }
        });
    }

    /**
     * Invocation-local ownership; a child borrows identity without owning its parent's kernel lease.
     *
     * @param ownedLease
     *     Kernel lease owned here, or {@code null} for a verified child build.
     * @param identity
     *     Verified invocation identity.
     */
    private record InvocationLease(@Nullable BuildOutputLease ownedLease, BuildLeaseIdentity identity)
    {
    }
}
