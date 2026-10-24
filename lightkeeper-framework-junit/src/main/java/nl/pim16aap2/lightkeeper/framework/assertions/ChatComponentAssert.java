package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/** Frozen semantic assertions associating literal text with effective click actions and payloads. */
public final class ChatComponentAssert extends AbstractAssert<ChatComponentAssert, @Nullable ChatComponentSnapshot>
{
    ChatComponentAssert(@Nullable ChatComponentSnapshot actual)
    {
        super(actual, ChatComponentAssert.class);
    }

    /**
     * Checks literal text associated with a click action.
     *
     * @param text Nonempty literal text fragment.
     * @return This assertion.
     */
    public ChatComponentAssert hasClickableText(String text)
    {
        validateText(text);
        return hasMatchingClick(component -> component.hasClickableText(text), "clickable literal text '" + text + "'");
    }

    /**
     * Checks the exact command associated with a literal text fragment.
     *
     * @param text Nonempty literal text fragment.
     * @param command Exact run-command payload.
     * @return This assertion.
     */
    public ChatComponentAssert runsCommand(String text, String command)
    {
        validateText(text);
        Objects.requireNonNull(command, "command may not be null.");
        return hasMatchingClick(component -> component.hasRunCommand(text, command),
            "literal text '%s' running command '%s'".formatted(text, command));
    }

    /**
     * Checks the exact suggested command associated with literal text.
     *
     * @param text Nonempty literal text fragment.
     * @param command Exact suggested payload, including trailing spaces.
     * @return This assertion.
     */
    public ChatComponentAssert suggestsCommand(String text, String command)
    {
        validateText(text);
        Objects.requireNonNull(command, "command may not be null.");
        return hasMatchingClick(component -> component.hasSuggestedCommand(text, command),
            "literal text '%s' suggesting command '%s'".formatted(text, command));
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the frozen component.
    private ChatComponentAssert hasMatchingClick(Predicate<ChatComponentSnapshot> predicate, String expectation)
    {
        isNotNull();
        final boolean matched;
        try
        {
            matched = predicate.test(actual);
        }
        catch (IllegalArgumentException malformed)
        {
            throw new IllegalStateException("Failed to parse component JSON: " + malformed.getMessage(), malformed);
        }
        if (!matched)
            failWithMessage("Expected %s, but component was %s.", expectation, actual.json());
        return this;
    }

    private static void validateText(String text)
    {
        Objects.requireNonNull(text, "text may not be null.");
        if (text.isEmpty())
            throw new IllegalArgumentException("text may not be empty.");
    }
}
