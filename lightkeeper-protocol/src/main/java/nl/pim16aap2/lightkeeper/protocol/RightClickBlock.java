package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Simulates the player right-clicking a block.
 */
public final class RightClickBlock
{
    private RightClickBlock()
    {
    }

    /**
     * Command record for {@code RIGHT_CLICK_BLOCK}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player performing the click.
     * @param x
     *     Target block X coordinate.
     * @param y
     *     Target block Y coordinate.
     * @param z
     *     Target block Z coordinate.
     * @param blockFace
     *     Bukkit {@code BlockFace} enum name indicating the face that was clicked.
     */
    public record Command(
        String requestId,
        UUID uuid,
        int x,
        int y,
        int z,
        String blockFace
    ) implements IAgentCommand<Response>
    {
        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code RIGHT_CLICK_BLOCK}.
     *
     * @param requestId
     *     Correlated request id.
     * @param cancelled
     *     Whether the interaction event was cancelled by a plugin.
     */
    public record Response(
        String requestId,
        boolean cancelled
    ) implements IAgentResponse
    {
    }
}
