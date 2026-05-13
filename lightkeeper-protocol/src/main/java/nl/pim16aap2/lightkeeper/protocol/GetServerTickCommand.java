package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests the current server tick counter.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 */
public record GetServerTickCommand(String requestId) implements IAgentCommand
{
}
