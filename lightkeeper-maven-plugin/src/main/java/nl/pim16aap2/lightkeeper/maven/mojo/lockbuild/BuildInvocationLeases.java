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
 * Serial snapshot export releases it before Failsafe; a later module's initialize guard acquires a new lease.
 * Parallel reactors retain ownership until invocation exit because another module may still be changing outputs.
 */
public final class BuildInvocationLeases
{
    /**
     * Invocation entries live while producer ownership is held. Parallel entries stay open until OS process exit;
     * a shutdown hook must not release them while other Maven threads can still write during JVM shutdown.
     */
    private static final ConcurrentHashMap<MavenSession, BuildOutputLease> LEASES = new ConcurrentHashMap<>();

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
     * Reports whether this invocation currently protects its producer outputs.
     *
     * @param session
     *     Current Maven session, when available.
     * @return Whether immutable snapshot capture is protected by this invocation's early guard.
     */
    public static boolean isHeld(@Nullable MavenSession session)
    {
        return session != null && LEASES.containsKey(session);
    }

    /**
     * Releases serial invocation outputs after snapshot capture, including skipped preparation.
     * Parallel builds retain ownership until actual JVM exit, including after a module fails.
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
        if (!LEASES.containsKey(session) || session.isParallel())
        {
            return;
        }
        finishInvocation(session);
    }

    static void finishInvocation(MavenSession session)
    {
        final BuildOutputLease lease = LEASES.remove(session);
        if (lease != null)
        {
            lease.close();
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

    private static BuildOutputLease acquireNew(MavenSession session, Duration timeout, Log log)
    {
        final Path root = Path.of(Objects.requireNonNull(
            session.getExecutionRootDirectory(),
            "Maven did not provide its reactor root directory."
        ));
        log.info("LK_BUILD_LOCK: Waiting for producer outputs in '" + root + "'.");
        final BuildOutputLease lease = BuildOutputLease.acquire(root, timeout);
        log.info("LK_BUILD_LOCK: Acquired producer outputs in '" + root + "'.");
        return lease;
    }
}
