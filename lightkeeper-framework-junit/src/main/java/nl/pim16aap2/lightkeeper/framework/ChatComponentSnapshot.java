package nl.pim16aap2.lightkeeper.framework;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Snapshot of a chat component.
 *
 * <p>The {@link #json()} is the server's native component-codec output, verbatim from the outbound packet. On
 * Minecraft 1.21.5+ that codec spells the click field {@code click_event} (snake_case), so the click helpers on
 * this record key on {@code click_event}, not the pre-1.21.5 {@code clickEvent}.
 *
 * @param json
 *     The raw JSON representation of the component.
 */
public record ChatComponentSnapshot(
    String json
)
{
    /**
     * Shared JSON mapper for click-event inspection, matching the shared-mapper convention used elsewhere in the
     * framework instead of allocating a fresh mapper per call.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * The {@code click_event} action name of a component that runs a command when clicked.
     */
    private static final String RUN_COMMAND_ACTION = "run_command";
    /**
     * The {@code click_event} action name of a component that fills the chat box with a command when clicked.
     */
    private static final String SUGGEST_COMMAND_ACTION = "suggest_command";

    /**
     * Extracts the command of the first {@code run_command} click event in this component's tree.
     *
     * @return The command to run (typically leading-slash prefixed), or empty when this component carries no
     *     {@code run_command} click event or its JSON cannot be parsed.
     */
    public Optional<String> clickRunCommand()
    {
        return findClickCommand(RUN_COMMAND_ACTION);
    }

    /**
     * Extracts the command of the first {@code suggest_command} click event in this component's tree.
     *
     * <p>The payload is returned verbatim, including any trailing space (suggest-command payloads often end with
     * a space to prime the next argument), so it is never trimmed.
     *
     * @return The command to suggest, or empty when this component carries no {@code suggest_command} click event
     *     or its JSON cannot be parsed.
     */
    public Optional<String> clickSuggestedCommand()
    {
        return findClickCommand(SUGGEST_COMMAND_ACTION);
    }

    /**
     * Finds the command payload of the first click event matching the requested action anywhere in the tree.
     *
     * @param action
     *     The {@code click_event} action name to match.
     * @return The matching click event's command payload, or empty when none matches.
     */
    private Optional<String> findClickCommand(String action)
    {
        try
        {
            final JsonNode root = OBJECT_MAPPER.readTree(json);
            // findValues walks the whole tree, so a click_event nested in an 'extra' child is found, not only a
            // root-level one. Matching per action (rather than "the first click_event found") means
            // clickRunCommand() never returns a suggest_command payload when a single message carries both
            // (for example a paginated [<] [1/3] [>] control).
            for (final JsonNode clickEvent : root.findValues("click_event"))
            {
                if (action.equals(clickEvent.path("action").asString("")) && clickEvent.hasNonNull("command"))
                    return Optional.of(clickEvent.get("command").asString());
            }
            return Optional.empty();
        }
        catch (JacksonException malformed)
        {
            // Malformed component JSON degrades to "no click command", matching the framework's existing
            // error-tolerance for this data (see PlayerHandleAssert's parse-failure handling).
            return Optional.empty();
        }
    }
}
