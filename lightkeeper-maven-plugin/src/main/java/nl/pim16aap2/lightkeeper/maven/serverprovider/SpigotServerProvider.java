package nl.pim16aap2.lightkeeper.maven.serverprovider;

import lombok.ToString;
import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import org.apache.maven.plugin.logging.Log;

/**
 * Represents a provider for the Spigot server type.
 * <p>
 * This implementation downloads {@code BuildTools.jar} and builds the Spigot server JAR file.
 */
@ToString(callSuper = true)
public class SpigotServerProvider extends ServerProvider
{
    private static final String SERVER_NAME = "spigot";

    public SpigotServerProvider(Log log, ServerSpecification serverSpecification)
    {
        super(log, SERVER_NAME, serverSpecification);
    }

    @Override
    public void prepareServer()
    {
        log().info("Preparing spigot server with provider: \n" + this);
    }
}
