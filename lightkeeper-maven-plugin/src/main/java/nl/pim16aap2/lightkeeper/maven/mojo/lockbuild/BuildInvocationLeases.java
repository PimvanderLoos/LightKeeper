package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import nl.pim16aap2.lightkeeper.runtime.BuildOutputLease;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protects selected module outputs across root/submodule entry points and parallel Maven execution.
 * Acquire all module leases in canonical path order before producer work starts. The owning CLI process retains
 * them until actual OS exit; shutdown hooks must not release ownership while Maven threads can still write.
 */
public final class BuildInvocationLeases
{
    /** Invocation-owned channels remain open through every producer and snapshot in that Maven process. */
    private static final ConcurrentHashMap<MavenSession, List<BuildOutputLease>> LEASES = new ConcurrentHashMap<>();

    private BuildInvocationLeases()
    {
    }

    /**
     * Acquires or reuses ownership of every selected module's outputs before compilation or packaging.
     *
     * @param session
     *     Live Maven invocation with its selected projects.
     * @param timeout
     *     Maximum total positive acquisition time across all selected modules.
     * @param log
     *     Maven lifecycle log.
     */
    public static void acquire(MavenSession session, Duration timeout, Log log)
    {
        if (timeout.isZero() || timeout.isNegative())
        {
            throw new IllegalArgumentException("Build lease timeout must be positive.");
        }
        LEASES.computeIfAbsent(session, ignored -> acquireNew(session, timeout, log));
    }

    /**
     * Reports whether this invocation owns its selected output leases.
     *
     * @param session
     *     Current Maven session, when available.
     * @return Whether immutable capture is protected by the invocation's early guard.
     */
    public static boolean isHeld(@Nullable MavenSession session)
    {
        return session != null && LEASES.containsKey(session);
    }

    /**
     * Explicitly finishes an invocation after all its projects have stopped writing outputs.
     * Standard CLI execution relies on OS process exit instead, including failure/cancellation and shutdown hooks.
     *
     * @param session
     *     Completed invocation, or {@code null} when no invocation was attached.
     */
    public static void release(@Nullable MavenSession session)
    {
        if (session != null)
        {
            final List<BuildOutputLease> leases = LEASES.remove(session);
            if (leases != null)
            {
                close(leases);
            }
        }
    }

    private static List<BuildOutputLease> acquireNew(MavenSession session, Duration timeout, Log log)
    {
        final List<BuildOutputLease> leases = new ArrayList<>();
        try
        {
            final TreeSet<Path> modules = new TreeSet<>();
            for (final MavenProject project : session.getProjects())
            {
                modules.add(project.getBasedir().toPath().toRealPath());
            }
            if (modules.isEmpty())
            {
                throw new IllegalStateException("Maven did not provide any selected projects for output ownership.");
            }
            final long started = System.nanoTime();
            for (final Path module : modules)
            {
                final long remaining = timeout.toNanos() - (System.nanoTime() - started);
                if (remaining <= 0)
                {
                    throw new IllegalStateException("Timed out acquiring selected Maven output leases.");
                }
                log.info("LK_BUILD_LOCK: Waiting for producer outputs in '" + module + "'.");
                leases.add(BuildOutputLease.acquire(module, Duration.ofNanos(remaining)));
                log.info("LK_BUILD_LOCK: Acquired producer outputs in '" + module + "'.");
            }
            return List.copyOf(leases);
        }
        catch (IOException | RuntimeException exception)
        {
            try
            {
                close(leases);
            }
            catch (RuntimeException cleanupFailure)
            {
                exception.addSuppressed(cleanupFailure);
            }
            throw new IllegalStateException("Cannot protect the selected Maven module outputs.", exception);
        }
    }

    private static void close(List<BuildOutputLease> leases)
    {
        RuntimeException failure = null;
        for (final BuildOutputLease lease : leases.reversed())
        {
            try
            {
                lease.close();
            }
            catch (RuntimeException exception)
            {
                if (failure == null)
                {
                    failure = exception;
                }
                else
                {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null)
        {
            throw failure;
        }
    }
}
