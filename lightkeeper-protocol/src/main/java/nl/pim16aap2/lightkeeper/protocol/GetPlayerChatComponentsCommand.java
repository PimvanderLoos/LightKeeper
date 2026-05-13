package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Returns accumulated chat component JSON strings received by the player via NMS packets.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player whose chat components are requested.
 */
public record GetPlayerChatComponentsCommand(
    String requestId,
    UUID uuid
) implements IAgentCommand
{
}
