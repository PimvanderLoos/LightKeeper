package nl.pim16aap2.lightkeeper.framework;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Frozen native chat-component JSON with shared literal-text and effective-click interpretation.
 *
 * <p>Literal strings, component arrays and extra children are supported, including inherited click events.
 * Translation keys, selectors, scores, keybinds and hover contents are not rendered as literal main-message text.
 *
 * @param json
 *     Native component-codec JSON, retained verbatim.
 */
public record ChatComponentSnapshot(String json)
{
    /** Shared mapper for immutable component inspection. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Modern and legacy click-event field names, in preference order. */
    private static final List<String> CLICK_EVENT_KEYS = List.of("click_event", "clickEvent");

    /** Validates the immutable JSON input reference without decoding it eagerly. */
    public ChatComponentSnapshot
    {
        Objects.requireNonNull(json, "json may not be null.");
    }

    /** @return First run-command payload, or empty for absent actions or malformed input. */
    public Optional<String> clickRunCommand()
    {
        return findClickCommand("run_command");
    }

    /** @return First suggest-command payload verbatim, including trailing spaces; empty for malformed input. */
    public Optional<String> clickSuggestedCommand()
    {
        return findClickCommand("suggest_command");
    }

    /**
     * Decodes contiguous literal runs and their effective click actions.
     *
     * <p>Adjacent literal runs with the same action and payload are combined. Non-clickable text and opaque
     * non-literal content prevent accidental association across unrelated portions of a message.
     *
     * @return Immutable literal click actions; textless style nodes may carry an empty text run.
     *
     * @throws IllegalArgumentException
     *     When JSON or supported component structure cannot be decoded.
     */
    public List<ClickAction> clickActions()
    {
        final List<LiteralRun> runs = new ArrayList<>();
        try
        {
            final JsonNode root = OBJECT_MAPPER.readTree(json);
            if (root == null)
            {
                throw new IllegalArgumentException("Component JSON is empty.");
            }
            readRuns(root, null, runs);
        }
        catch (JacksonException exception)
        {
            throw new IllegalArgumentException("Malformed chat component JSON.", exception);
        }

        final List<ClickAction> actions = new ArrayList<>();
        ClickEvent active = null;
        final StringBuilder text = new StringBuilder();
        for (final var run : runs)
        {
            if (!Objects.equals(active, run.event()))
            {
                appendAction(actions, active, text);
                text.setLength(0);
                active = run.event();
            }
            if (active != null)
            {
                text.append(run.text());
            }
        }
        appendAction(actions, active, text);
        return List.copyOf(actions);
    }

    /**
     * Matches decoded literal text associated with an effective click event.
     *
     * @param fragment
     *     Nonempty literal text fragment.
     * @return Whether a matching clickable literal run exists.
     */
    public boolean hasClickableText(String fragment)
    {
        validateFragment(fragment);
        return clickActions().stream()
            .anyMatch(action -> action.text().contains(fragment));
    }

    /**
     * Matches literal text and its exact run-command payload.
     *
     * @param fragment
     *     Nonempty literal text fragment.
     * @param command
     *     Exact command payload.
     * @return Whether the same effective click action matches both requirements.
     */
    public boolean hasRunCommand(String fragment, String command)
    {
        return hasCommand(fragment, "run_command", command);
    }

    /**
     * Matches literal text and its exact suggest-command payload, preserving spaces.
     *
     * @param fragment
     *     Nonempty literal text fragment.
     * @param command
     *     Exact suggested payload.
     * @return Whether the same effective click action matches both requirements.
     */
    public boolean hasSuggestedCommand(String fragment, String command)
    {
        return hasCommand(fragment, "suggest_command", command);
    }

    /**
     * Frozen literal text and its associated effective action.
     *
     * @param text
     *     Contiguous literal text; empty for a textless style node.
     * @param action
     *     Encoded click action name.
     * @param value
     *     Text payload when represented by command, value or URL; otherwise null.
     */
    public record ClickAction(String text, String action, @Nullable String value)
    {
        /** Validates the immutable action identity and literal text. */
        public ClickAction
        {
            Objects.requireNonNull(text, "text may not be null.");
            Objects.requireNonNull(action, "action may not be null.");
            if (action.isBlank())
            {
                throw new IllegalArgumentException("action may not be blank.");
            }
        }
    }

    /**
     * @param action
     *     Encoded action.
     * @param value
     *     Optional text payload.
     */
    private record ClickEvent(String action, @Nullable String value)
    {
    }

    /**
     * @param text
     *     Literal text, or empty for an opaque boundary.
     * @param event
     *     Effective event, null at a boundary.
     */
    private record LiteralRun(String text, @Nullable ClickEvent event)
    {
    }

    private boolean hasCommand(String fragment, String action, String command)
    {
        validateFragment(fragment);
        Objects.requireNonNull(command, "command may not be null.");
        return clickActions().stream()
            .anyMatch(
                click -> click.text().contains(fragment) &&
                    action.equals(click.action()) &&
                    command.equals(click.value())
            );
    }

    private Optional<String> findClickCommand(String action)
    {
        try
        {
            for (final var click : clickActions())
            {
                final String value = click.value();
                if (action.equals(click.action()) && value != null)
                {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
        catch (IllegalArgumentException malformed)
        {
            return Optional.empty();
        }
    }

    private static void validateFragment(String fragment)
    {
        Objects.requireNonNull(fragment, "fragment may not be null.");
        if (fragment.isEmpty())
        {
            throw new IllegalArgumentException("fragment may not be empty.");
        }
    }

    private static void readRuns(JsonNode node, @Nullable ClickEvent inherited, List<LiteralRun> runs)
    {
        if (node.isString())
        {
            appendLiteral(runs, node.asString(), inherited);
            return;
        }
        if (node.isArray())
        {
            if (node.isEmpty())
            {
                throw new IllegalArgumentException("Component array must not be empty.");
            }
            final JsonNode first = node.path(0);
            readRuns(first, inherited, runs);
            final ClickEvent parent = firstStyle(first, inherited);
            for (int index = 1; index < node.size(); index++)
            {
                readRuns(node.path(index), parent, runs);
            }
            return;
        }
        if (!node.isObject())
        {
            throw new IllegalArgumentException("Expected a literal string, component object or component array.");
        }

        final ClickEvent effective = clickFor(node, inherited);
        if (node.has("text"))
        {
            final JsonNode text = node.path("text");
            if (!text.isString())
            {
                throw new IllegalArgumentException("Literal component text must be a string.");
            }
            appendLiteral(runs, text.asString(), effective);
        }
        else
        {
            appendLiteral(runs, "", effective);
            runs.add(new LiteralRun("", null));
        }

        final JsonNode extra = node.path("extra");
        if (extra.isMissingNode())
        {
            return;
        }
        if (!extra.isArray())
        {
            throw new IllegalArgumentException("Component extra children must be an array.");
        }
        for (final JsonNode child : extra)
        {
            readRuns(child, effective, runs);
        }
    }

    private static @Nullable ClickEvent firstStyle(JsonNode node, @Nullable ClickEvent inherited)
    {
        if (node.isArray())
        {
            return firstStyle(node.path(0), inherited);
        }
        return node.isObject() ? clickFor(node, inherited) : inherited;
    }

    private static @Nullable ClickEvent clickFor(JsonNode node, @Nullable ClickEvent inherited)
    {
        for (final String key : CLICK_EVENT_KEYS)
        {
            final JsonNode click = node.path(key);
            if (click.isMissingNode() || click.isNull())
            {
                continue;
            }
            if (!click.isObject() ||
                !click.path("action").isString() ||
                click.path("action").asString().isBlank())
            {
                throw new IllegalArgumentException("Click event must contain a nonblank action name.");
            }
            String value = null;
            for (final String field : List.of("command", "value", "url"))
            {
                final JsonNode payload = click.path(field);
                if (payload.isMissingNode() || payload.isNull())
                {
                    continue;
                }
                if (!payload.isString())
                {
                    throw new IllegalArgumentException("Click text payload must be a string.");
                }
                value = payload.asString();
                break;
            }
            return new ClickEvent(click.path("action").asString(), value);
        }
        return inherited;
    }

    private static void appendLiteral(List<LiteralRun> runs, String text, @Nullable ClickEvent event)
    {
        if (!text.isEmpty() || event != null)
        {
            runs.add(new LiteralRun(text, event));
        }
    }

    private static void appendAction(List<ClickAction> actions, @Nullable ClickEvent event, StringBuilder text)
    {
        if (event != null)
        {
            actions.add(new ClickAction(text.toString(), event.action(), event.value()));
        }
    }
}
