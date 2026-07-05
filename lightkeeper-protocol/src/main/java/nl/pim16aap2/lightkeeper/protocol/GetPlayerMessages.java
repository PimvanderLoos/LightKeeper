package nl.pim16aap2.lightkeeper.protocol;

import java.util.List;
import java.util.Objects;
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
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(uuid, "uuid");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code GET_PLAYER_MESSAGES}.
     *
     * @param messages
     *     Accumulated plain-text messages received by the player.
     */
    public record Response(
        List<String> messages
    ) implements IAgentResponse
    {
        /**
         * Validates and defensively copies the message history.
         */
        public Response
        {
            messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        }
    }
}
