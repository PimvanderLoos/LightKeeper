package nl.pim16aap2.lightkeeper.provisioning.serverprocess;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/** Paper-specific process type using the shared baseline initialization lifecycle. */
public class PaperServerProcess extends MinecraftServerProcess
{
    /**
     * Creates a Paper initialization process.
     *
     * @param serverDirectory
     *     Working directory.
     * @param jarFile
     *     Prepared server JAR.
     * @param javaExecutable
     *     Selected Java executable.
     * @param extraJvmArgs
     *     Optional additional server JVM arguments.
     * @param memoryMb
     *     Maximum heap size in MiB.
     */
    public PaperServerProcess(
        Path serverDirectory,
        Path jarFile,
        String javaExecutable,
        @Nullable String extraJvmArgs,
        int memoryMb)
    {
        super(serverDirectory, jarFile, javaExecutable, extraJvmArgs, memoryMb);
    }
}
