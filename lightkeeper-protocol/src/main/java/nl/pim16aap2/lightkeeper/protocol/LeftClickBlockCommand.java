package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Simulates the player left-clicking a block.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player performing the click.
 * @param x
 *     Target block X coordinate.
 * @param y
 *     Target block Y coordinate.
 * @param z
 *     Target block Z coordinate.
 * @param blockFace
 *     Bukkit {@code BlockFace} enum name indicating the face that was clicked.
 */
public record LeftClickBlockCommand(
    String requestId,
    UUID uuid,
    int x,
    int y,
    int z,
    String blockFace
) implements IAgentCommand
{
}
