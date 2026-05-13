package nl.pim16aap2.lightkeeper.protocol;

/**
 * Returns all captured events of the given class as property-map snapshots.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param eventClassName
 *     Fully-qualified class name of the Bukkit event whose captured instances are requested.
 */
public record GetCapturedEventsCommand(
    String requestId,
    String eventClassName
) implements IAgentCommand
{
}
