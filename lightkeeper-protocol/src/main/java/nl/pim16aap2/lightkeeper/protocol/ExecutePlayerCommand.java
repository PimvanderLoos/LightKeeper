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
     *     Full command string without a leading slash.
     */
    public record Command(
        String requestId,
        UUID uuid,
        String command
    ) implements IAgentCommand<Response>
    {
        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code EXECUTE_PLAYER_COMMAND}.
     *
     * @param requestId
     *     Correlated request id.
     * @param dispatched
     *     Whether the underlying {@code performCommand}/{@code dispatchCommand} call returned {@code true}.
     */
    public record Response(
        String requestId,
        boolean dispatched
    ) implements IAgentResponse
    {
    }
}
