package nl.pim16aap2.lightkeeper.protocol;

/**
 * Arms deterministic cancellation of the next N fired events of a class.
 */
public final class CancelNextEvents
{
    private CancelNextEvents()
    {
    }

    /**
     * Command record for {@code CANCEL_NEXT_EVENTS}.
     *
     * <p>The agent registers a LOWEST-priority listener (so it acts before regular plugin listeners) that
     * cancels the next {@code count} fired events of the class; the MONITOR-priority capture listener still
     * observes every event with its final cancelled state.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param eventClassName
     *     Fully-qualified class name of the Bukkit event to cancel; must implement {@code Cancellable}.
     * @param count
     *     How many upcoming events to cancel; must be positive.
     */
    public record Command(
        String requestId,
        String eventClassName,
        int count
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonBlank(eventClassName, "eventClassName");
            if (count <= 0)
                throw new IllegalArgumentException("'count' must be positive.");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code CANCEL_NEXT_EVENTS}.
     */
    public record Response() implements IAgentResponse
    {
    }
}
