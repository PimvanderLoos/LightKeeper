package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests the namespaced material key of the block at the given position.
 */
public final class BlockType
{
    private BlockType()
    {
    }

    /**
     * Command record for {@code BLOCK_TYPE}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param worldName
     *     Name of the world containing the block.
     * @param x
     *     Block X coordinate.
     * @param y
     *     Block Y coordinate.
     * @param z
     *     Block Z coordinate.
     */
    public record Command(
        String requestId,
        String worldName,
        int x,
        int y,
        int z
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonBlank(worldName, "worldName");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code BLOCK_TYPE}.
     *
     * @param material
     *     Namespaced material key of the block at the requested position.
     * @param blockData
     *     Full block-data string of the block (Bukkit {@code BlockData#getAsString()}), carrying every state
     *     property, e.g. {@code minecraft:lever[face=floor,facing=north,powered=true]}.
     */
    public record Response(
        String material,
        String blockData
    ) implements IAgentResponse
    {
    }
}
