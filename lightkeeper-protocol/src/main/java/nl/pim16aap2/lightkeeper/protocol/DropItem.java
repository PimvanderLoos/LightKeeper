package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Simulates a main-hand item drop event for the player.
 *
 * <p>The item entity is created and immediately removed regardless of event cancellation; the response indicates
 * whether the drop event was cancelled by a plugin.
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
