package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Returns a JSON snapshot of the player's non-air inventory slots.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player whose inventory is requested.
 */
public record GetPlayerInventoryCommand(
    String requestId,
    UUID uuid
) implements IAgentCommand
{
}
