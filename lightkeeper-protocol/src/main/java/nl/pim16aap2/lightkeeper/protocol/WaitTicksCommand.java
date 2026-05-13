package nl.pim16aap2.lightkeeper.protocol;

/**
 * Waits for the given number of server ticks before responding.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param ticks
 *     Number of ticks to wait (1 tick ≈ 50 ms).
 */
public record WaitTicksCommand(
    String requestId,
    int ticks
) implements IAgentCommand
{
}
