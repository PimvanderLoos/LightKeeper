package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Drops the player's main-hand item into the world.
 *
 * <p>Fires a {@code PlayerDropItemEvent}. If the event is cancelled the item entity is removed and the player's
 * inventory is unchanged. If the event is not cancelled the item entity stays in the world and one item is consumed
 * from the player's main hand.
 */
public final class DropItem
{
    private DropItem()
    {
    }

    /**
     * Command record for {@code DROP_ITEM}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player performing the drop.
     */
    public record Command(
        String requestId,
        UUID uuid
    ) implements IAgentCommand<Response>
    {
        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code DROP_ITEM}.
     *
     * @param requestId
     *     Correlated request id.
     * @param eventCancelled
     *     Whether the {@code PlayerDropItemEvent} was cancelled by a plugin.
     *     {@code true} means the drop was blocked; {@code false} means it was allowed.
     */
    public record Response(
        String requestId,
        boolean eventCancelled
    ) implements IAgentResponse
    {
    }
}
