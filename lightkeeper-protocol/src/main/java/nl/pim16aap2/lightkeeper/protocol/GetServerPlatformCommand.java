package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests the server platform identifier (PAPER, SPIGOT, or UNKNOWN).
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 */
public record GetServerPlatformCommand(String requestId) implements IAgentCommand
{
}
