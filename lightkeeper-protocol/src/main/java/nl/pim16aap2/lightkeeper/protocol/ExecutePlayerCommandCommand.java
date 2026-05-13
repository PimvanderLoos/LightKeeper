package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Executes a command as the given synthetic player.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player who issues the command.
 * @param command
 *     Full command string without a leading slash.
 */
public record ExecutePlayerCommandCommand(
    String requestId,
    UUID uuid,
    String command
) implements IAgentCommand
{
}
