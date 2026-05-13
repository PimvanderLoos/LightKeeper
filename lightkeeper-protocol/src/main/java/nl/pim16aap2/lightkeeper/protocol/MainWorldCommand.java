package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests the name of the main server world.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 */
public record MainWorldCommand(String requestId) implements IAgentCommand
{
}
