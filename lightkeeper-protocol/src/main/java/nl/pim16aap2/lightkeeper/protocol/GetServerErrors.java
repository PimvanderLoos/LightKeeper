package nl.pim16aap2.lightkeeper.protocol;

import java.util.List;

/**
 * Returns all server errors and warnings captured by the agent's log appender since agent load or the last
 * {@code CLEAR_SERVER_ERRORS}.
 */
public final class GetServerErrors
{
    private GetServerErrors()
    {
    }

    /**
     * Command record for {@code GET_SERVER_ERRORS}.
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
     * Response record for {@code GET_SERVER_ERRORS}.
     *
     * @param errors
     *     Captured error entries ordered oldest-to-newest.
     * @param droppedCount
     *     Number of entries discarded because the agent-side capture buffer was full. The buffer keeps the
     *     oldest entries, so the retained entries always include the first (root-cause) errors.
     * @param captureActive
     *     Whether the agent's log appender is attached. {@code false} means structured capture is unavailable
     *     on this server and the entry list is not authoritative.
     */
    public record Response(
        List<ServerErrorEntry> errors,
        long droppedCount,
        boolean captureActive
    ) implements IAgentResponse
    {
        /**
         * Defensively copies the error list.
         */
        public Response
        {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }
}
