package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Executes a command as the given synthetic player.
 */
public final class ExecutePlayerCommand
{
    private ExecutePlayerCommand()
    {
    }

    /**
     * Command record for {@code EXECUTE_PLAYER_COMMAND}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player who issues the command.
     * @param command
     *     Full command string, optionally beginning with a leading slash.
     */
    public record Command(
        String requestId,
        UUID uuid,
        String command
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates that a command was supplied and normalizes surrounding whitespace.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(uuid, "uuid");
            if (command == null || command.isBlank())
                throw new IllegalArgumentException("'command' must not be blank.");
            command = command.strip();
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code EXECUTE_PLAYER_COMMAND}.
     *
     * @param dispatched
     *     Whether the underlying {@code performCommand}/{@code dispatchCommand} call returned {@code true}.
     */
    public record Response(
        boolean dispatched
    ) implements IAgentResponse
    {
    }
}
