package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests a chunk to be unloaded.
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
public record UnloadChunkCommand(
    String requestId,
    String worldName,
    int x,
    int z
) implements IAgentCommand
{
}
