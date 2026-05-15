package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Simulates an inventory drag placing the given material into the specified slots.
 */
public final class DragMenuSlots
{
    private DragMenuSlots()
    {
    }

    /**
     * Command record for {@code DRAG_MENU_SLOTS}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player performing the drag.
     * @param materialKey
     *     Namespaced key (e.g. {@code minecraft:stone}) or plain Bukkit {@code Material} enum name of the item being
     *     dragged.
     * @param slots
     *     Zero-based slot indices that are the targets of the drag operation.
     */
    public record Command(
        String requestId,
        UUID uuid,
        String materialKey,
        int[] slots
    ) implements IAgentCommand<Response>
    {
        public Command
        {
            if (slots == null)
                throw new IllegalArgumentException("'slots' must not be null.");
            slots = slots.clone();
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code DRAG_MENU_SLOTS}.
     *
     * @param requestId
     *     Correlated request id.
     */
    public record Response(String requestId) implements IAgentResponse
    {
    }
}
