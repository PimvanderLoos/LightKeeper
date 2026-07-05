package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests the current server tick counter.
 */
public final class GetServerTick
{
    private GetServerTick()
    {
    }

    /**
     * Command record for {@code GET_SERVER_TICK}.
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
     * Response record for {@code GET_SERVER_TICK}.
     *
     * @param requestId
     *     Correlated request id.
     * @param tick
     *     Current monotonic server tick counter value.
     */
    public record Response(
        String requestId,
        long tick
    ) implements IAgentResponse
    {
    }
}
