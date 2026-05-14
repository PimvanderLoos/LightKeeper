package nl.pim16aap2.lightkeeper.protocol;

/**
 * Registers a dynamic Bukkit event listener that captures events of the given class.
 */
public final class RegisterEventListener
{
    private RegisterEventListener()
    {
    }

    /**
     * Command record for {@code REGISTER_EVENT_LISTENER}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param eventClassName
     *     Fully-qualified class name of the Bukkit event to listen for.
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
     * Response record for {@code REGISTER_EVENT_LISTENER}.
     *
     * @param requestId
     *     Correlated request id.
     */
    public record Response(String requestId) implements IAgentResponse
    {
    }
}
