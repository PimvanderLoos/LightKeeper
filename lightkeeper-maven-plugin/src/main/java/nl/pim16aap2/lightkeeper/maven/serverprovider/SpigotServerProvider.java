package nl.pim16aap2.lightkeeper.maven.serverprovider;

import lombok.ToString;
import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a provider for the Spigot server type.
 * <p>
 * This implementation downloads {@code BuildTools.jar} and builds the Spigot server JAR file.
 */
@ToString(callSuper = true)
public class SpigotServerProvider extends ServerProvider
{
    private static final String SERVER_NAME = "spigot";
    private static final String BUILD_TOOLS_JAR_URL =
        "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar";
    private static final String BUILD_TOOLS_JAR_NAME = "BuildTools.jar";

    public SpigotServerProvider(Log log, ServerSpecification serverSpecification)
    {
        super(log, SERVER_NAME, serverSpecification);
    }

    private void buildSpigotServerJar(Path buildToolsJarPath)
        throws MojoExecutionException
    {
        ProcessBuilder processBuilder = new ProcessBuilder(
            Objects.requireNonNullElse(serverSpecification().javaExecutablePath(), "java"),
            "-jar",
            buildToolsJarPath.toAbsolutePath().toString(),
            "--rev",
            serverSpecification().serverVersion()
        );

        processBuilder.directory(jarCacheDirectory().toFile());
        processBuilder.inheritIO();

        int result;
        try
        {
            final var process = processBuilder.start();
            result = process.waitFor();
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrupted while waiting for BuildTools process to finish", exception);
        }
        catch (Exception exception)
        {
            throw new MojoExecutionException("Failed to start BuildTools process", exception);
        }

        if (result != 0)
        {
            throw new MojoExecutionException(
                "BuildTools process failed with exit code %d. Please check the output for more details."
                    .formatted(result)
            );
        }

        if (!Files.exists(jarCacheFile()))
        {
            throw new MojoExecutionException(
                "BuildTools did not produce the expected server JAR file: %s. Please check the output for errors."
                    .formatted(jarCacheFile())
            );
        }
    }

    @Override
    protected void createBaseServerJar()
        throws MojoExecutionException
    {
        final Path buildToolsJarPath =
            jarCacheDirectory().resolve(BUILD_TOOLS_JAR_NAME);

        log().info("Building Spigot server JAR file using BuildTools...");

        downloadFile(BUILD_TOOLS_JAR_URL, buildToolsJarPath);

        buildSpigotServerJar(buildToolsJarPath);

        // Run `java -jar ../BuildTools.jar --rev ${serverVersion.serverVersion()}` in the jar cache directory to generate the correct files.
        // Or ${serverSpecification.javaExecutablePath()}?
    }

    @Override
    protected void createBaseServer()
        throws MojoExecutionException
    {
        acceptEula();
        writeServerProperties("""
            enable-query=true
            enable-rcon=true
            rcon.password=lightkeeper
            """
        );

        // First, configure the server:
        // - Create a `server.properties`:
        //   - Enable rcon, to be used with https://github.com/jobfeikens/rcon
        //

        // Run `java --Xmx${serverSpecification.memoryMb()}M --Xms${serverSpecification.memoryMb()}M -jar ${getOutputJarFileName()} ${serverSpecification.extraJvmArgs()} -jar ${targetJarFile} -nogui` in the ${targetServerDirectory} directory.
        // Or ${serverSpecification.javaExecutablePath()}?
        // Alternatively, run it in a container! Instead of javaExecutablePath, use image name/tag.
    }
}
