package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Despawns and removes a synthetic player.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player to remove.
 */
public record RemovePlayerCommand(
    String requestId,
    UUID uuid
) implements IAgentCommand
{
}
