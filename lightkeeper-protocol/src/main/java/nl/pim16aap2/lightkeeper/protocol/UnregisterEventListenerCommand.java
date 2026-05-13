package nl.pim16aap2.lightkeeper.protocol;

/**
 * Unregisters the dynamic event listener for the given class.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param eventClassName
 *     Fully-qualified class name of the Bukkit event whose listener should be removed.
 */
public record UnregisterEventListenerCommand(
    String requestId,
    String eventClassName
) implements IAgentCommand
{
}
