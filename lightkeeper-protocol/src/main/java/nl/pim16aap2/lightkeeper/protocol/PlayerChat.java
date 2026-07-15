package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Makes a synthetic player say a chat message, firing the real chat event.
 */
public final class PlayerChat
{
    private PlayerChat()
    {
    }

    /**
     * Command record for {@code PLAYER_CHAT}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the chatting player.
     * @param message
     *     The chat message.
     */
    public record Command(
        String requestId,
        UUID uuid,
        String message
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(uuid, "uuid");
            ProtocolPreconditions.requireNonBlank(message, "message");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code PLAYER_CHAT}.
     */
    public record Response() implements IAgentResponse
    {
    }
}
