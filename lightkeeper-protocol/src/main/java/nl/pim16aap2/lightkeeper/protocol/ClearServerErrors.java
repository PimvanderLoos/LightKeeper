package nl.pim16aap2.lightkeeper.protocol;

/**
 * Discards all server errors captured by the agent's log appender and resets its dropped-entry counter.
 */
public final class ClearServerErrors
{
    private ClearServerErrors()
    {
    }

    /**
     * Command record for {@code CLEAR_SERVER_ERRORS}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     */
    public record Command(
        String requestId
    ) implements IAgentCommand<Response>
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
     * Response record for {@code CLEAR_SERVER_ERRORS}.
     */
    public record Response() implements IAgentResponse
    {
    }
}
