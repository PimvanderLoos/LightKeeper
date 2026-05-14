package nl.pim16aap2.lightkeeper.protocol;

/**
 * Clears the captured event buffer for the given class.
 */
public final class ClearCapturedEvents
{
    private ClearCapturedEvents()
    {
    }

    /**
     * Command record for {@code CLEAR_CAPTURED_EVENTS}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param eventClassName
     *     Fully-qualified class name of the Bukkit event whose buffer is to be cleared.
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
     * Response record for {@code CLEAR_CAPTURED_EVENTS}.
     *
     * @param requestId
     *     Correlated request id.
     */
    public record Response(String requestId) implements IAgentResponse
    {
    }
}
