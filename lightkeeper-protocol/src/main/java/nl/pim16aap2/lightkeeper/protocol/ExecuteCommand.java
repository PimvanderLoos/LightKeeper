package nl.pim16aap2.lightkeeper.protocol;

/**
 * Executes a server-side command as the given source (CONSOLE or PLAYER).
 */
public final class ExecuteCommand
{
    private ExecuteCommand()
    {
    }

    /**
     * Command record for {@code EXECUTE_COMMAND}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param commandSource
     *     Wire name of the {@code CommandSource} enum identifying who issues the command.
     * @param command
     *     Full command string without a leading slash.
     */
    public record Command(
        String requestId,
        String commandSource,
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
     * Response record for {@code EXECUTE_COMMAND}.
     *
     * @param requestId
     *     Correlated request id.
     * @param success
     *     Whether the command dispatch reported success.
     */
    public record Response(
        String requestId,
        boolean success
    ) implements IAgentResponse
    {
    }
}
