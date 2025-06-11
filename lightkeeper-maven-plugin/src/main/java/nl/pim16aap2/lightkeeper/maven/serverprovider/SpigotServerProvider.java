package nl.pim16aap2.lightkeeper.maven.serverprovider;

import org.apache.maven.plugin.logging.Log;

/**
 * Represents a provider for the Spigot server type.
 * <p>
 * This implementation downloads {@code BuildTools.jar} and builds the Spigot server JAR file.
 */
public class SpigotServerProvider extends ServerProvider
{
    private static final String SERVER_NAME = "Spigot";

    public SpigotServerProvider(Log log)
    {
        super(log, SERVER_NAME);
    }
}
