package nl.pim16aap2.lightkeeper.runtime;

/**
 * Shared runtime protocol constants for LightKeeper v1.
 */
public final class RuntimeProtocol
{
    public static final String VERSION = "v1";

    public static final String PROPERTY_SOCKET_PATH = "lightkeeper.agent.socketPath";
    public static final String PROPERTY_AUTH_TOKEN = "lightkeeper.agent.authToken";
    public static final String PROPERTY_PROTOCOL_VERSION = "lightkeeper.agent.protocolVersion";
    public static final String PROPERTY_EXPECTED_AGENT_SHA256 = "lightkeeper.agent.expectedSha256";

    private RuntimeProtocol()
    {
    }
}
