package nl.pim16aap2.lightkeeper.protocol;

/**
 * Initial connection handshake. Must be sent before any other command.
 */
public final class Handshake
{
    private Handshake()
    {
    }

    /**
     * Command record for {@code HANDSHAKE}.
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
    public record Command(
        String requestId,
        String token,
        int protocolVersion,
        String agentSha256
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonBlank(token, "token");
            ProtocolPreconditions.requireNonNull(agentSha256, "agentSha256");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code HANDSHAKE}.
     *
     * @param requestId
     *     Correlated request id.
     * @param protocolVersion
     *     Protocol version confirmed by the server.
     * @param bukkitVersion
     *     Bukkit version string of the running server.
     */
    public record Response(
        String requestId,
        int protocolVersion,
        String bukkitVersion
    ) implements IAgentResponse
    {
    }
}
