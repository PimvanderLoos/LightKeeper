package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Teleports a synthetic player to the given location.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player to teleport.
 * @param worldName
 *     Name of the destination world.
 * @param x
 *     Destination X coordinate.
 * @param y
 *     Destination Y coordinate.
 * @param z
 *     Destination Z coordinate.
 */
public record TeleportPlayerCommand(
    String requestId,
    UUID uuid,
    String worldName,
    double x,
    double y,
    double z
) implements IAgentCommand
{
}
