package nl.pim16aap2.lightkeeper.protocol;

/**
 * Unregisters the dynamic event listener for the given class.
 */
public final class UnregisterEventListener
{
    private UnregisterEventListener()
    {
    }

    /**
     * Command record for {@code UNREGISTER_EVENT_LISTENER}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param eventClassName
     *     Fully-qualified class name of the Bukkit event whose listener should be removed.
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
     * Response record for {@code UNREGISTER_EVENT_LISTENER}.
     *
     * @param requestId
     *     Correlated request id.
     */
    public record Response(String requestId) implements IAgentResponse
    {
    }
}
