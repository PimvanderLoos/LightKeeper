package nl.pim16aap2.lightkeeper.framework.internal;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches the {@link OrphanReaper} watchdog process for a started Minecraft server.
 * <p>
 * On POSIX systems the watchdog is detached into its own session via {@code setsid} when available. This matters
 * because CI harnesses (e.g. GNU {@code timeout}) commonly kill the whole process group on timeout — a watchdog in
 * the test JVM's process group would die in the same blast radius it is meant to survive. Without {@code setsid}
 * (e.g. on Windows), the watchdog still covers single-PID kills of the test JVM (targeted OOM kill, {@code kill -9}),
 * just not whole-group kills.
 * <p>
 * Launch failures are logged and swallowed: the reaper is a safety net, not a prerequisite — a missing watchdog must
 * never fail a test run that would otherwise work.
 */
final class OrphanReaperLauncher
{
    private static final System.Logger LOG = System.getLogger(OrphanReaperLauncher.class.getName());

    private static final List<Path> SETSID_CANDIDATES = List.of(
        Path.of("/usr/bin/setsid"),
        Path.of("/bin/setsid")
    );

    private OrphanReaperLauncher()
    {
    }

    /**
     * Starts a watchdog process that kills the given server process tree when the given JVM dies.
     * <p>
     * Note: when the watchdog is wrapped in {@code setsid}, the returned process may be the (short-lived) wrapper
     * rather than the watchdog itself. That is fine for the eager-destroy use case: the watchdog always exits on its
     * own once it observes the dead server.
     *
     * @param watchedJvmPid
     *     The PID of the JVM whose death should trigger the reap (in production: the current test JVM).
     * @param serverPid
     *     The PID of the Minecraft server process to reap.
     * @return The started watchdog process, or {@code null} if it could not be started.
     */
    static @Nullable Process launch(long watchedJvmPid, long serverPid)
    {
        final Path codeLocation = resolveCodeLocation();
        if (codeLocation == null)
            return null;

        final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        final List<String> command = new ArrayList<>();
        final Path setsid = findSetsid();
        if (setsid != null)
            command.add(setsid.toString());
        command.add(javaExecutable.toString());
        command.add("-Xms16m");
        command.add("-Xmx32m");
        command.add("-cp");
        command.add(codeLocation.toString());
        command.add(OrphanReaper.class.getName());
        command.add(Long.toString(watchedJvmPid));
        command.add(Long.toString(startEpochMillisOf(watchedJvmPid)));
        command.add(Long.toString(serverPid));
        command.add(Long.toString(startEpochMillisOf(serverPid)));

        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

        try
        {
            final Process process = processBuilder.start();
            closeUnusedStdin(process);
            LOG.log(
                System.Logger.Level.DEBUG,
                "Started orphan reaper (pid {0}, setsid: {1}) watching JVM {2}, guarding server {3}.",
                process.pid(), setsid != null, watchedJvmPid, serverPid
            );
            return process;
        }
        catch (Exception exception)
        {
            LOG.log(
                System.Logger.Level.WARNING,
                "Failed to start the orphan reaper; a killed test JVM may leak the Minecraft server process.",
                exception
            );
            return null;
        }
    }

    /**
     * Reads a process's start time for the PID-identity check in the watchdog.
     *
     * @param pid
     *     The PID whose start time to read.
     * @return The start time in epoch milliseconds, or {@link OrphanReaper#START_INSTANT_UNKNOWN} when unavailable.
     */
    private static long startEpochMillisOf(long pid)
    {
        return ProcessHandle
            .of(pid)
            .map(OrphanReaper.WatchedProcess::startEpochMillis)
            .orElse(OrphanReaper.START_INSTANT_UNKNOWN);
    }

    /**
     * Locates a usable {@code setsid} executable for session detachment, if any.
     * <p>
     * Package-private so tests can gate session-detachment assertions on the same discovery logic.
     *
     * @return The path to {@code setsid}, or {@code null} when not available on this system.
     */
    static @Nullable Path findSetsid()
    {
        for (final Path candidate : SETSID_CANDIDATES)
        {
            if (Files.isExecutable(candidate))
                return candidate;
        }

        // Fall back to a PATH scan for container images with non-standard layouts.
        final String pathEnvironment = System.getenv("PATH");
        if (pathEnvironment == null || pathEnvironment.isBlank())
            return null;
        for (final String entry : pathEnvironment.split(java.io.File.pathSeparator, -1))
        {
            if (entry.isBlank())
                continue;
            final Path candidate = Path.of(entry).resolve("setsid");
            if (Files.isExecutable(candidate))
                return candidate;
        }
        return null;
    }

    /**
     * Closes the never-used stdin pipe to the watchdog; the other streams are already discarded.
     *
     * @param process
     *     The started watchdog process.
     */
    private static void closeUnusedStdin(Process process)
    {
        try
        {
            process.getOutputStream().close();
        }
        catch (IOException exception)
        {
            LOG.log(System.Logger.Level.DEBUG, "Failed to close the orphan reaper's stdin pipe.", exception);
        }
    }

    /**
     * Resolves the classpath entry (jar or classes directory) that contains {@link OrphanReaper}.
     *
     * @return The code location, or {@code null} when it cannot be determined (e.g. exotic classloaders).
     */
    private static @Nullable Path resolveCodeLocation()
    {
        try
        {
            final var codeSource = OrphanReaper.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null)
            {
                LOG.log(
                    System.Logger.Level.WARNING,
                    "Cannot determine the code location of the orphan reaper; skipping watchdog startup."
                );
                return null;
            }
            return Path.of(codeSource.getLocation().toURI());
        }
        catch (URISyntaxException | RuntimeException exception)
        {
            LOG.log(
                System.Logger.Level.WARNING,
                "Failed to resolve the code location of the orphan reaper; skipping watchdog startup.",
                exception
            );
            return null;
        }
    }
}
