package nl.pim16aap2.lightkeeper.protocol;

import java.util.List;
import java.util.UUID;

/**
 * Returns all accumulated plain-text messages received by the player.
 */
public final class GetPlayerMessages
{
    private GetPlayerMessages()
    {
    }

    /**
     * Command record for {@code GET_PLAYER_MESSAGES}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player whose messages are requested.
     */
    public record Command(
        String requestId,
        UUID uuid
    ) implements IAgentCommand<Response>
    {
        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code GET_PLAYER_MESSAGES}.
     *
     * @param requestId
     *     Correlated request id.
     * @param messages
     *     Accumulated plain-text messages received by the player.
     */
    public record Response(
        String requestId,
        List<String> messages
    ) implements IAgentResponse
    {
    }
}
