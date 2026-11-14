package nl.pim16aap2.lightkeeper.provisioning;

import java.io.InputStream;

/**
 * Canonical metadata and resource access for the embedded LightKeeper Spigot runtime agent.
 */
public final class LightkeeperEmbeddedAgent
{
    /**
     * Absolute classpath resource path for the embedded agent jar.
     */
    public static final String RESOURCE_PATH = "/embedded/lightkeeper-agent-spigot.jar";
    /**
     * Fixed output file name used when provisioning the embedded agent into a server.
     */
    public static final String FILE_NAME = "lightkeeper-agent-spigot.jar";

    private LightkeeperEmbeddedAgent()
    {
    }

    /**
     * Opens the embedded agent jar stream from the shared provisioning classpath.
     *
     * @return Input stream for the embedded agent jar.
     *
     * @throws ProvisioningException
     *     When the embedded resource is missing.
     */
    public static InputStream openStream()
        throws ProvisioningException
    {
        final InputStream inputStream = LightkeeperEmbeddedAgent.class.getResourceAsStream(RESOURCE_PATH);
        if (inputStream == null)
        {
            throw new ProvisioningException(
                "Embedded LightKeeper agent resource '%s' is missing from the shared provisioning artifact."
                    .formatted(RESOURCE_PATH)
            );
        }
        return inputStream;
    }
}
