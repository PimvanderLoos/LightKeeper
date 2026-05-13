package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests the namespaced material key of the block at the given position.
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
 */
public record BlockTypeCommand(
    String requestId,
    String worldName,
    int x,
    int y,
    int z
) implements IAgentCommand
{
}
