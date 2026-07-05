package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests the name of the main server world.
 */
public final class MainWorld
{
    private MainWorld()
    {
    }

    /**
     * Command record for {@code MAIN_WORLD}.
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
     * Response record for {@code MAIN_WORLD}.
     *
     * @param worldName
     *     Name of the primary server world.
     */
    public record Response(
        String worldName
    ) implements IAgentResponse
    {
    }
}
