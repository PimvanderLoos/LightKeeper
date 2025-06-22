package nl.pim16aap2.lightkeeper.maven.serverprocess;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public class SpigotServerProcess extends MinecraftServerProcess
{
    public SpigotServerProcess(
        Path serverDirectory,
        Path jarFile,
        String javaExecutable,
        @Nullable String extraJvmArgs,
        int memoryMb)
    {
        super(serverDirectory, jarFile, javaExecutable, extraJvmArgs, memoryMb);
    }
}
