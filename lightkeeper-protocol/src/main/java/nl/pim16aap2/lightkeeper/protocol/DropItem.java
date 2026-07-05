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
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(uuid, "uuid");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code DROP_ITEM}.
     *
     * @param dropped
     *     {@code true} when the drop materialised (item entity created, inventory slot consumed);
     *     {@code false} when the player had nothing in hand, or the {@code PlayerDropItemEvent} was cancelled.
     */
    public record Response(
        boolean dropped
    ) implements IAgentResponse
    {
    }
}
