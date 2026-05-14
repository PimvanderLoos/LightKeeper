package nl.pim16aap2.lightkeeper.protocol;

/**
 * Requests the server platform identifier (PAPER, SPIGOT, or UNKNOWN).
 */
public final class GetServerPlatform
{
    private GetServerPlatform()
    {
    }

    /**
     * Command record for {@code GET_SERVER_PLATFORM}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     */
    public record Command(String requestId) implements IAgentCommand<Response>
    {
        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code GET_SERVER_PLATFORM}.
     *
     * @param requestId
     *     Correlated request id.
     * @param serverName
     *     Server implementation name (e.g. {@code CraftBukkit}, {@code Paper}).
     * @param serverVersion
     *     Full server version string.
     */
    public record Response(
        String requestId,
        String serverName,
        String serverVersion
    ) implements IAgentResponse
    {
    }
}
