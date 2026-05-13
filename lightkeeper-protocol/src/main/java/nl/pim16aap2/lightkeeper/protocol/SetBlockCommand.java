package nl.pim16aap2.lightkeeper.protocol;

/**
 * Sets the block at the given position.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param worldName
 *     Name of the world containing the block.
 * @param x
 *     Block X coordinate.
 * @param y
 *     Block Y coordinate.
 * @param z
 *     Block Z coordinate.
 * @param materialKey
 *     Namespaced key (e.g. {@code minecraft:stone}) or plain Bukkit {@code Material} enum name identifying the
 *     block type to place.
 */
public record SetBlockCommand(
    String requestId,
    String worldName,
    int x,
    int y,
    int z,
    String materialKey
) implements IAgentCommand
{
}
