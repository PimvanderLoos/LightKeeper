package nl.pim16aap2.lightkeeper.protocol;

/**
 * Returns all captured events of the given class as property-map snapshots.
 */
public final class GetCapturedEvents
{
    private GetCapturedEvents()
    {
    }

    /**
     * Command record for {@code GET_CAPTURED_EVENTS}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param eventClassName
     *     Fully-qualified class name of the Bukkit event whose captured instances are requested.
     */
    public record Command(
        String requestId,
        String eventClassName
    ) implements IAgentCommand<Response>
    {
        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code GET_CAPTURED_EVENTS}.
     *
     * @param requestId
     *     Correlated request id.
     * @param eventsJson
     *     JSON array of captured event property snapshots.
     */
    public record Response(
        String requestId,
        String eventsJson
    ) implements IAgentResponse
    {
    }
}
