package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Simulates the player placing a block at the given position.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param uuid
 *     Unique identifier of the player performing the placement.
 * @param materialKey
 *     Namespaced key (e.g. {@code minecraft:stone}) or plain Bukkit {@code Material} enum name of the block to
 *     place.
 * @param x
 *     Target block X coordinate.
 * @param y
 *     Target block Y coordinate.
 * @param z
 *     Target block Z coordinate.
 */
public record PlacePlayerBlockCommand(
    String requestId,
    UUID uuid,
    String materialKey,
    int x,
    int y,
    int z
) implements IAgentCommand
{
}
