package nl.pim16aap2.lightkeeper.framework.internal;

import org.jspecify.annotations.Nullable;

import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * Launches the {@link OrphanReaper} watchdog process for a started Minecraft server.
 * <p>
 * Launch failures are logged and swallowed: the reaper is a safety net, not a prerequisite — a missing watchdog must
 * never fail a test run that would otherwise work.
 */
final class OrphanReaperLauncher
{
    private static final System.Logger LOG = System.getLogger(OrphanReaperLauncher.class.getName());

    private OrphanReaperLauncher()
    {
    }

    /**
     * Starts a watchdog process that kills the given server process tree when the given JVM dies.
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
        final ProcessBuilder processBuilder = new ProcessBuilder(
            javaExecutable.toString(),
            "-Xms16m",
            "-Xmx32m",
            "-cp",
            codeLocation.toString(),
            OrphanReaper.class.getName(),
            Long.toString(watchedJvmPid),
            Long.toString(serverPid)
        );
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

        try
        {
            final Process process = processBuilder.start();
            LOG.log(
                System.Logger.Level.DEBUG,
                "Started orphan reaper (pid {0}) watching JVM {1}, guarding server {2}.",
                process.pid(), watchedJvmPid, serverPid
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
