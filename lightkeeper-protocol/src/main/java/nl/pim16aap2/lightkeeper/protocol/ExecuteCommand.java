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
     *     Who issues the command.
     * @param command
     *     Full command string without a leading slash.
     */
    public record Command(
        String requestId,
        CommandSource commandSource,
        String command
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(commandSource, "commandSource");
            ProtocolPreconditions.requireNonBlank(command, "command");
        }

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
     * @param dispatched
     *     Whether the underlying {@code dispatchCommand} call returned {@code true}.
     */
    public record Response(
        String requestId,
        boolean dispatched
    ) implements IAgentResponse
    {
    }
}
