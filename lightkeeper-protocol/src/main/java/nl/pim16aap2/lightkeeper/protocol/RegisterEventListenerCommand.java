package nl.pim16aap2.lightkeeper.protocol;

/**
 * Registers a dynamic Bukkit event listener that captures events of the given class.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param eventClassName
 *     Fully-qualified class name of the Bukkit event to listen for.
 */
public record RegisterEventListenerCommand(
    String requestId,
    String eventClassName
) implements IAgentCommand
{
}
