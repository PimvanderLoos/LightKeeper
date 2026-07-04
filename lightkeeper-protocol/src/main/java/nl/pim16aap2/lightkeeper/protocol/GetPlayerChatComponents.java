package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Returns accumulated chat component JSON strings received by the player via NMS packets.
 */
public final class GetPlayerChatComponents
{
    private GetPlayerChatComponents()
    {
    }

    /**
     * Command record for {@code GET_PLAYER_CHAT_COMPONENTS}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player whose chat components are requested.
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
     * Response record for {@code GET_PLAYER_CHAT_COMPONENTS}.
     *
     * @param requestId
     *     Correlated request id.
     * @param componentsJson
     *     JSON array containing the accumulated chat component payloads.
     */
    public record Response(
        String requestId,
        String componentsJson
    ) implements IAgentResponse
    {
    }
}
