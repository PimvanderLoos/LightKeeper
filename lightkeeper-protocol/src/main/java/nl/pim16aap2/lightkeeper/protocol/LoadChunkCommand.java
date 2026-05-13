package nl.pim16aap2.lightkeeper.protocol;

/**
 * Forces a chunk to load.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param worldName
 *     Name of the world containing the chunk.
 * @param x
 *     Chunk X coordinate (not block coordinate).
 * @param z
 *     Chunk Z coordinate (not block coordinate).
 */
public record LoadChunkCommand(
    String requestId,
    String worldName,
    int x,
    int z
) implements IAgentCommand
{
}
