package nl.pim16aap2.lightkeeper.protocol;

/**
 * Executes a server-side command as the given source (CONSOLE or PLAYER).
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param commandSource
 *     Wire name of the {@code CommandSource} enum identifying who issues the command.
 * @param command
 *     Full command string without a leading slash.
 */
public record ExecuteCommandCommand(
    String requestId,
    String commandSource,
    String command
) implements IAgentCommand
{
}
