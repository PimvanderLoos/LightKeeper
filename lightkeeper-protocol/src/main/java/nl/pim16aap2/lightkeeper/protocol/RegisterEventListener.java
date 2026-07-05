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
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonBlank(eventClassName, "eventClassName");
            if (!eventClassName.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
                throw new IllegalArgumentException(
                    "'eventClassName' must be a fully-qualified class name, got: " + eventClassName);
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code REGISTER_EVENT_LISTENER}.
     *
     */
    public record Response() implements IAgentResponse
    {
    }
}
