package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests the server platform identifier (PAPER, SPIGOT, or UNKNOWN).
 */
public final class GetServerPlatform
{
    private GetServerPlatform()
    {
    }

    /**
     * Command record for {@code GET_SERVER_PLATFORM}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     */
    public record Command(String requestId) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code GET_SERVER_PLATFORM}.
     *
     * @param requestId
     *     Correlated request id.
     * @param platform
     *     Canonical platform identifier ({@code PAPER}, {@code SPIGOT}, or {@code UNKNOWN}).
     */
    public record Response(
        String requestId,
        String platform
    ) implements IAgentResponse
    {
    }
}
