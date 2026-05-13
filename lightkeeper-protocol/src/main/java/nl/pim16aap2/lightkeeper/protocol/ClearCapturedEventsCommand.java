package nl.pim16aap2.lightkeeper.protocol;

/**
 * Clears the captured event buffer for the given class.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param eventClassName
 *     Fully-qualified class name of the Bukkit event whose buffer is to be cleared.
 */
public record ClearCapturedEventsCommand(
    String requestId,
    String eventClassName
) implements IAgentCommand
{
}
