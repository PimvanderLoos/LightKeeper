package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Returns a snapshot of the inventory menu currently open for the player, or an empty response if none.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player whose open menu is requested.
 */
public record GetOpenMenuCommand(
    String requestId,
    UUID uuid
) implements IAgentCommand
{
}
