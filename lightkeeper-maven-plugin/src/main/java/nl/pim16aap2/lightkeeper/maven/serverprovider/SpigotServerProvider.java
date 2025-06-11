package nl.pim16aap2.lightkeeper.maven.serverprovider;

import lombok.ToString;
import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.nio.file.Path;

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

    @Override
    protected void createBaseServerJar()
        throws MojoExecutionException
    {
        final Path BUILD_TOOLS_JAR_PATH =
            jarCacheDirectory().resolve(BUILD_TOOLS_JAR_NAME);

        log().info("Building Spigot server JAR file using BuildTools...");

        downloadFile(BUILD_TOOLS_JAR_URL, BUILD_TOOLS_JAR_PATH);

        // Run `java -jar ../BuildTools.jar --rev ${serverVersion.serverVersion()}` in the jar cache directory to generate the correct files.
        // Or ${serverSpecification.javaExecutablePath()}?
    }

    @Override
    protected void createBaseServer()
        throws MojoExecutionException
    {
        // First, configure the server:
        // - Accept the EULA by creating a file named `eula.txt` in the base server directory with the content `eula=true`.
        // - Create a `server.properties`:
        //   - Enable rcon, to be used with https://github.com/jobfeikens/rcon
        //

        // Run `java --Xmx${serverSpecification.memoryMb()}M --Xms${serverSpecification.memoryMb()}M -jar ${getOutputJarFileName()} ${serverSpecification.extraJvmArgs()} -jar ${targetJarFile} -nogui` in the ${targetServerDirectory} directory.
        // Or ${serverSpecification.javaExecutablePath()}?
        // Alternatively, run it in a container! Instead of javaExecutablePath, use image name/tag.
    }
}
