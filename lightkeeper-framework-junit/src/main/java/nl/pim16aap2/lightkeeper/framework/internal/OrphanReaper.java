package nl.pim16aap2.lightkeeper.framework.internal;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * Standalone watchdog that force-kills the Minecraft server process tree when the test JVM dies.
 * <p>
 * This class runs as its own JVM process (see {@link OrphanReaperLauncher}) so that it survives a hard kill of the
 * test JVM (CI timeout, OOM killer, {@code kill -9}) — the one case a JVM shutdown hook cannot cover. Without it, a
 * killed test JVM leaks a running Minecraft server that holds the agent socket and the world session lock.
 * <p>
 * The watchdog polls both processes and exits as soon as either side is gone: if the server exits first there is
 * nothing to guard; if the watched (test) JVM exits first, the server process tree is destroyed forcibly. Process
 * identity is verified via the process start time where available, so a recycled PID is never mistaken for the
 * original process (a reused watched-JVM PID would otherwise disable the reap forever; a reused server PID would
 * get an unrelated process killed).
 * <p>
 * This class must only use JDK classes: it runs with nothing but the framework code location on its classpath.
 */
public final class OrphanReaper
{
    static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);

    /**
     * Sentinel for "process start time unknown"; identity checks are skipped for that side when set.
     */
    static final long START_INSTANT_UNKNOWN = -1L;

    private OrphanReaper()
    {
    }

    /**
     * Entry point for the watchdog process.
     *
     * @param args
     *     Exactly four arguments: the PID of the test JVM to watch, its start time in epoch milliseconds (or
     *     {@code -1} when unknown), the PID of the server process to reap, and its start time in epoch milliseconds
     *     (or {@code -1} when unknown).
     */
    public static void main(String[] args)
    {
        if (args.length != 4)
        {
            System.err.println(
                "Usage: OrphanReaper <watchedJvmPid> <watchedJvmStartEpochMillis> <serverPid> <serverStartEpochMillis>"
            );
            System.exit(2);
            return;
        }

        runFromArgs(args);
    }

    /**
     * Parses the validated arguments and runs the watchdog loop.
     *
     * @param args
     *     The four arguments described on {@link #main(String[])}.
     */
    static void runFromArgs(String... args)
    {
        final WatchedProcess watchedJvm = WatchedProcess.of(Long.parseLong(args[0]), Long.parseLong(args[1]));
        final WatchedProcess serverProcess = WatchedProcess.of(Long.parseLong(args[2]), Long.parseLong(args[3]));

        // A missing server process means there is nothing to guard. A missing watched JVM means it already died,
        // which run() handles by reaping immediately.
        if (serverProcess == null)
            return;

        run(watchedJvm, serverProcess, DEFAULT_POLL_INTERVAL);
    }

    /**
     * Watches both processes until one of them exits.
     * <p>
     * Returns when the server process is gone (either on its own, or because this method destroyed it after the
     * watched JVM died).
     *
     * @param watchedJvm
     *     The test JVM to watch. When {@code null} or dead, the server process tree is destroyed immediately.
     * @param serverProcess
     *     The server process to reap.
     * @param pollInterval
     *     How long to sleep between liveness checks.
     */
    static void run(@Nullable WatchedProcess watchedJvm, WatchedProcess serverProcess, Duration pollInterval)
    {
        while (true)
        {
            if (!serverProcess.isAliveAndSame())
                return;

            if (watchedJvm == null || !watchedJvm.isAliveAndSame())
            {
                killProcessTree(serverProcess);
                return;
            }

            try
            {
                Thread.sleep(pollInterval.toMillis());
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Forcibly destroys a process and all of its descendants.
     * <p>
     * Re-verifies the process's identity immediately before the kill: the process may exit and its PID be recycled
     * between the caller's liveness check and this call, and a stranger must never be destroyed.
     *
     * @param root
     *     The root of the process tree to destroy.
     */
    static void killProcessTree(WatchedProcess root)
    {
        if (!root.isAliveAndSame())
            return;
        root.handle().descendants().forEach(ProcessHandle::destroyForcibly);
        root.handle().destroyForcibly();
    }

    /**
     * A process handle paired with the start time it is expected to have.
     *
     * @param handle
     *     The handle to the process.
     * @param expectedStartEpochMillis
     *     The start time the process had when it was first observed, or {@link #START_INSTANT_UNKNOWN} to skip the
     *     identity check.
     */
    record WatchedProcess(ProcessHandle handle, long expectedStartEpochMillis)
    {
        /**
         * Looks up a process by PID.
         *
         * @param pid
         *     The PID to look up.
         * @param expectedStartEpochMillis
         *     The expected start time of the process, or {@link #START_INSTANT_UNKNOWN}.
         * @return The watched process, or {@code null} when no process with that PID exists.
         */
        static @Nullable WatchedProcess of(long pid, long expectedStartEpochMillis)
        {
            return ProcessHandle
                .of(pid)
                .map(handle -> new WatchedProcess(handle, expectedStartEpochMillis))
                .orElse(null);
        }

        /**
         * Whether the process is alive and still the process it was when first observed.
         * <p>
         * A start-time mismatch means the PID was recycled by an unrelated process, which is treated the same as
         * "the original process is gone". The check is skipped when either side's start time is unknown.
         *
         * @return {@code true} when the process is alive and its identity matches.
         */
        boolean isAliveAndSame()
        {
            if (!handle.isAlive())
                return false;
            if (expectedStartEpochMillis == START_INSTANT_UNKNOWN)
                return true;
            final long actualStartEpochMillis = startEpochMillis(handle);
            return actualStartEpochMillis == START_INSTANT_UNKNOWN
                || actualStartEpochMillis == expectedStartEpochMillis;
        }

        /**
         * Reads a process's start time.
         *
         * @param handle
         *     The process to read.
         * @return The start time in epoch milliseconds, or {@link #START_INSTANT_UNKNOWN} when unavailable.
         */
        static long startEpochMillis(ProcessHandle handle)
        {
            return handle.info().startInstant().map(Instant::toEpochMilli).orElse(START_INSTANT_UNKNOWN);
        }
    }
}
