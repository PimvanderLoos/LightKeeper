package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Simulates the player placing a block at the given position.
 */
public final class PlacePlayerBlock
{
    private PlacePlayerBlock()
    {
    }

    /**
     * Command record for {@code PLACE_PLAYER_BLOCK}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player performing the placement.
     * @param materialKey
     *     Namespaced key (e.g. {@code minecraft:stone}) or plain Bukkit {@code Material} enum name of the block to
     *     place.
     * @param x
     *     Target block X coordinate.
     * @param y
     *     Target block Y coordinate.
     * @param z
     *     Target block Z coordinate.
     */
    public record Command(
        String requestId,
        UUID uuid,
        String materialKey,
        int x,
        int y,
        int z
    ) implements IAgentCommand<Response>
    {
        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code PLACE_PLAYER_BLOCK}.
     *
     * @param requestId
     *     Correlated request id.
     * @param material
     *     Namespaced material key of the block that was placed.
     */
    public record Response(
        String requestId,
        String material
    ) implements IAgentResponse
    {
    }
}
