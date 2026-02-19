package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable runtime configuration for the Spigot agent.
 *
 * @param socketPath
 *     Absolute path of the Unix domain socket used for request/response communication.
 * @param authToken
 *     Shared secret expected during the protocol handshake.
 * @param protocolVersion
 *     Runtime protocol version that must match the client exactly.
 * @param expectedAgentSha256
 *     Optional expected SHA-256 hash of the agent artifact. Blank disables hash verification.
 */
record AgentConfiguration(
    Path socketPath,
    String authToken,
    int protocolVersion,
    String expectedAgentSha256
)
{
    /**
     * Builds validated configuration from runtime system properties.
     *
     * @return Parsed and validated configuration.
     *
     * @throws IllegalStateException
     *     When a required property is missing or blank.
     */
    static AgentConfiguration fromSystemProperties()
    {
        final String configuredSocketPath = requireNonBlankProperty(RuntimeProtocol.PROPERTY_SOCKET_PATH);
        final String authToken = requireNonBlankProperty(RuntimeProtocol.PROPERTY_AUTH_TOKEN);
        final int protocolVersion = parseRequiredIntProperty(RuntimeProtocol.PROPERTY_PROTOCOL_VERSION);
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
        Objects.requireNonNull(expectedAgentSha256, "expectedAgentSha256");
    }

    /**
     * Reads a required system property and rejects blank values.
     *
     * @param key
     *     The JVM system property key.
     * @return Non-blank property value.
     *
     * @throws IllegalStateException
     *     When the property is absent or blank.
     */
    private static String requireNonBlankProperty(String key)
    {
        final String value = System.getProperty(key, "");
        if (value.isBlank())
            throw new IllegalStateException("Required system property '%s' is missing.".formatted(key));
        return value;
    }

    private static int parseRequiredIntProperty(String key)
    {
        final String value = requireNonBlankProperty(key);
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException exception)
        {
            throw new IllegalStateException(
                "Required system property '%s' must be an integer but was '%s'."
                    .formatted(key, value),
                exception
            );
        }
    }
}
