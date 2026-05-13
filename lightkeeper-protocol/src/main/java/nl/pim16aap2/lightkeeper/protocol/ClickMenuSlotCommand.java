package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Simulates a click on the given inventory slot.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player performing the click.
 * @param slot
 *     Zero-based inventory slot index to click.
 * @param clickType
 *     Bukkit {@code ClickType} enum name describing the type of click to simulate.
 */
public record ClickMenuSlotCommand(
    String requestId,
    UUID uuid,
    int slot,
    String clickType
) implements IAgentCommand
{
}
