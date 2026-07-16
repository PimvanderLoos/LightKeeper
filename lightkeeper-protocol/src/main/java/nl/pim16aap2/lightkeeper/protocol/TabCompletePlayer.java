package nl.pim16aap2.lightkeeper.protocol;

import java.util.List;
import java.util.UUID;

/**
 * Computes command tab-completions as the given synthetic player.
 *
 * <p>The completions are produced by the server's live {@code CommandMap#tabComplete} call, so they are
 * permission-filtered for the requesting player exactly as a real client tab-complete would be.
 */
public final class TabCompletePlayer
{
    private TabCompletePlayer()
    {
    }

    /**
     * Command record for {@code TAB_COMPLETE_PLAYER}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player whose permissions and context drive the completion.
     * @param commandLine
     *     The raw command-line buffer to complete, optionally beginning with a leading slash. Surrounding
     *     whitespace is <b>not</b> stripped: a trailing space is semantically load-bearing here (for example
     *     {@code "gamemode "} completes the first argument, whereas {@code "gamemode"} completes the command
     *     name itself), so the buffer is preserved verbatim.
     */
    public record Command(
        String requestId,
        UUID uuid,
        String commandLine
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates the command inputs.
         *
         * <p>Unlike {@link ExecutePlayerCommand.Command}, the {@code commandLine} is intentionally not rejected
         * when blank and not stripped: a whitespace-only or trailing-whitespace buffer is a valid,
         * meaningfully-different tab-complete input rather than a malformed command string.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(uuid, "uuid");
            ProtocolPreconditions.requireNonNull(commandLine, "commandLine");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code TAB_COMPLETE_PLAYER}.
     *
     * @param completions
     *     The tab-completion suggestions in server order. A {@code null} {@code CommandMap#tabComplete} result
     *     (unknown command, or a {@code TabCompleter} returning {@code null}) is normalized to an empty list.
     */
    public record Response(
        List<String> completions
    ) implements IAgentResponse
    {
        /**
         * Defensively copies the completion list, defaulting a {@code null} to an empty list.
         */
        public Response
        {
            completions = completions == null ? List.of() : List.copyOf(completions);
        }
    }
}
