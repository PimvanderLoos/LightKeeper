package nl.pim16aap2.lightkeeper.protocol;

/**
 * Forces a chunk to load.
 */
public final class LoadChunk
{
    private LoadChunk()
    {
    }

    /**
     * Command record for {@code LOAD_CHUNK}.
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
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonBlank(worldName, "worldName");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code LOAD_CHUNK}.
     *
     * @param requestId
     *     Correlated request id.
     * @param loaded
     *     Whether the chunk was loaded.
     */
    public record Response(
        String requestId,
        boolean loaded
    ) implements IAgentResponse
    {
    }
}
