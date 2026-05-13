package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Simulates an inventory drag placing the given material into the specified slots.
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
public record DragMenuSlotsCommand(
    String requestId,
    UUID uuid,
    String materialKey,
    int[] slots
) implements IAgentCommand
{
}
