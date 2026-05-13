package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Simulates a main-hand item drop event for the player.
 *
 * <p>The item entity is created and immediately removed regardless of event cancellation; the response indicates
 * whether the event was allowed.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player performing the drop.
 */
public record DropItemCommand(
    String requestId,
    UUID uuid
) implements IAgentCommand
{
}
