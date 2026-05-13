package nl.pim16aap2.lightkeeper.protocol;

/**
 * Initial connection handshake. Must be sent before any other command.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param token
 *     Auth token used to authenticate the client against the server.
 * @param protocolVersion
 *     Expected protocol version; the server rejects the connection if versions differ.
 * @param agentSha256
 *     SHA-256 hash of the agent JAR. Blank value skips the integrity check.
 */
public record HandshakeCommand(
    String requestId,
    String token,
    int protocolVersion,
    String agentSha256
) implements IAgentCommand
{
}
