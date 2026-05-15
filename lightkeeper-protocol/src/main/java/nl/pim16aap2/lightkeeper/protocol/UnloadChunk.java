package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests a chunk to be unloaded.
 */
public final class UnloadChunk
{
    private UnloadChunk()
    {
    }

    /**
     * Command record for {@code UNLOAD_CHUNK}.
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
    public record Command(
        String requestId,
        String worldName,
        int x,
        int z
    ) implements IAgentCommand<Response>
    {
        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code UNLOAD_CHUNK}.
     *
     * @param requestId
     *     Correlated request id.
     * @param unloaded
     *     Whether the chunk was unloaded.
     */
    public record Response(
        String requestId,
        boolean unloaded
    ) implements IAgentResponse
    {
    }
}
