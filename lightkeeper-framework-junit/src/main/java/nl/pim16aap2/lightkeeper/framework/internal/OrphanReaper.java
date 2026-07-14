package nl.pim16aap2.lightkeeper.framework.internal;

import java.time.Duration;

/**
 * Standalone watchdog that force-kills the Minecraft server process tree when the test JVM dies.
 * <p>
 * This class runs as its own JVM process (see {@link OrphanReaperLauncher}) so that it survives a hard kill of the
 * test JVM (CI timeout, OOM killer, {@code kill -9}) — the one case a JVM shutdown hook cannot cover. Without it, a
 * killed test JVM leaks a running Minecraft server that holds the agent socket and the world session lock.
 * <p>
 * The watchdog polls both processes and exits as soon as either side is gone: if the server exits first there is
 * nothing to guard; if the watched (test) JVM exits first, the server process tree is destroyed forcibly.
 * <p>
 * This class must only use JDK classes: it runs with nothing but the framework code location on its classpath.
 */
public final class OrphanReaper
{
    static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);

    private OrphanReaper()
    {
    }

    /**
     * Entry point for the watchdog process.
     *
     * @param args
     *     Exactly two arguments: the PID of the test JVM to watch and the PID of the server process to reap.
     */
    public static void main(String[] args)
    {
        if (args.length != 2)
        {
            System.err.println("Usage: OrphanReaper <watchedJvmPid> <serverPid>");
            System.exit(2);
        }

        final ProcessHandle watchedJvm = ProcessHandle.of(Long.parseLong(args[0])).orElse(null);
        final ProcessHandle serverProcess = ProcessHandle.of(Long.parseLong(args[1])).orElse(null);

        // A missing server process means there is nothing to guard. A missing watched JVM means it already died,
        // which run() handles by reaping immediately.
        if (serverProcess == null)
            return;

        run(watchedJvm, serverProcess, DEFAULT_POLL_INTERVAL);
    }

    /**
     * Watches both processes until one of them exits.
     * <p>
     * Returns when the server process has exited (either on its own, or because this method destroyed it after the
     * watched JVM died).
     *
     * @param watchedJvm
     *     The test JVM to watch. When {@code null} or dead, the server process tree is destroyed immediately.
     * @param serverProcess
     *     The server process to reap.
     * @param pollInterval
     *     How long to sleep between liveness checks.
     */
    static void run(ProcessHandle watchedJvm, ProcessHandle serverProcess, Duration pollInterval)
    {
        while (true)
        {
            if (!serverProcess.isAlive())
                return;

            if (watchedJvm == null || !watchedJvm.isAlive())
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
     *
     * @param root
     *     The root of the process tree to destroy.
     */
    static void killProcessTree(ProcessHandle root)
    {
        root.descendants().forEach(ProcessHandle::destroyForcibly);
        root.destroyForcibly();
    }
}
