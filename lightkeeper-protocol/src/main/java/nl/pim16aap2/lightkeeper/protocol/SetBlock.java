package nl.pim16aap2.lightkeeper.protocol;

/**
 * Sets the block at the given position.
 */
public final class SetBlock
{
    private SetBlock()
    {
    }

    /**
     * Command record for {@code SET_BLOCK}.
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
     * @param materialKey
     *     Namespaced key (e.g. {@code minecraft:stone}) or plain Bukkit {@code Material} enum name identifying
     *     the block type to place.
     */
    public record Command(
        String requestId,
        String worldName,
        int x,
        int y,
        int z,
        String materialKey
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonBlank(worldName, "worldName");
            ProtocolPreconditions.requireNonBlank(materialKey, "materialKey");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code SET_BLOCK}.
     *
     * @param material
     *     Bukkit material name of the block that was placed.
     */
    public record Response(
        String material
    ) implements IAgentResponse
    {
    }
}
