package nl.pim16aap2.lightkeeper.protocol;

/**
 * Creates a new world on the server.
 */
public final class NewWorld
{
    private NewWorld()
    {
    }

    /**
     * Command record for {@code NEW_WORLD}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param worldName
     *     Unique name for the new world.
     * @param worldType
     *     World generator type; corresponds to the {@code WorldType} enum name (e.g. {@code FLAT} or {@code NORMAL}).
     * @param environment
     *     Bukkit {@code World.Environment} enum name (e.g. {@code NORMAL}, {@code NETHER}, {@code THE_END}).
     * @param seed
     *     World generation seed.
     */
    public record Command(
        String requestId,
        String worldName,
        String worldType,
        String environment,
        long seed
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonBlank(worldName, "worldName");
            ProtocolPreconditions.requireNonBlank(worldType, "worldType");
            ProtocolPreconditions.requireNonBlank(environment, "environment");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code NEW_WORLD}.
     *
     * @param requestId
     *     Correlated request id.
     * @param worldName
     *     Name of the created or loaded world.
     */
    public record Response(
        String requestId,
        String worldName
    ) implements IAgentResponse
    {
    }
}
