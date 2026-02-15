package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;

import java.nio.file.Path;
import java.util.Objects;

record AgentConfiguration(
    Path socketPath,
    String authToken,
    String protocolVersion,
    String expectedAgentSha256)
{
    static AgentConfiguration fromSystemProperties()
    {
        final String configuredSocketPath = requireNonBlankProperty(RuntimeProtocol.PROPERTY_SOCKET_PATH);
        final String authToken = requireNonBlankProperty(RuntimeProtocol.PROPERTY_AUTH_TOKEN);
        final String protocolVersion = requireNonBlankProperty(RuntimeProtocol.PROPERTY_PROTOCOL_VERSION);
        final String expectedAgentSha256 = System.getProperty(RuntimeProtocol.PROPERTY_EXPECTED_AGENT_SHA256, "");

        return new AgentConfiguration(
            Path.of(configuredSocketPath).toAbsolutePath(),
            authToken,
            protocolVersion,
            expectedAgentSha256
        );
    }

    AgentConfiguration
    {
        Objects.requireNonNull(socketPath, "socketPath");
        Objects.requireNonNull(authToken, "authToken");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(expectedAgentSha256, "expectedAgentSha256");
    }

    private static String requireNonBlankProperty(String key)
    {
        final String value = System.getProperty(key, "");
        if (value.isBlank())
            throw new IllegalStateException("Required system property '%s' is missing.".formatted(key));
        return value;
    }
}
