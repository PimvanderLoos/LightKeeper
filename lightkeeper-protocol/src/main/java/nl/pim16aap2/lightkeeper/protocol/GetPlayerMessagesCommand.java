package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Returns all accumulated plain-text messages received by the player.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player whose messages are requested.
 */
public record GetPlayerMessagesCommand(
    String requestId,
    UUID uuid
) implements IAgentCommand
{
}
