package nl.pim16aap2.lightkeeper.framework.assertions;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Assertions for synthetic player state.
 */
public final class PlayerHandleAssert extends AbstractAssert<PlayerHandleAssert, @Nullable PlayerHandle>
{
    /**
     * Shared JSON mapper for chat-component inspection, matching the shared-mapper convention elsewhere in the
     * framework instead of allocating a fresh mapper per assertion call.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    PlayerHandleAssert(@Nullable PlayerHandle actual)
    {
        super(actual, PlayerHandleAssert.class);
    }

    /**
     * Asserts that the player has the expected name.
     *
     * @param expectedName
     *     Expected player name.
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert hasName(String expectedName)
    {
        final var actual = nonNullActual();
        if (!Objects.equals(actual.name(), expectedName))
            failWithMessage("Expected player name '%s' but was '%s'.", expectedName, actual.name());
        return this;
    }

    /**
     * Asserts that the player has an item with the specified material in their inventory.
     *
     * @param materialKey
     *     Material key (e.g. "minecraft:stone").
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert hasItemInInventory(String materialKey)
    {
        final var actual = nonNullActual();
        final var item = actual.inventory().findItem(materialKey);
        if (item == null)
        {
            failWithMessage("Expected player '%s' to have item '%s' in inventory, but it was not found.",
                actual.name(), materialKey);
        }
        return this;
    }

    /**
     * Asserts that the player does not have an item with the specified material in their inventory.
     *
     * @param materialKey
     *     Material key (e.g. "minecraft:stone").
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert doesNotHaveItemInInventory(String materialKey)
    {
        final var actual = nonNullActual();
        final var item = actual.inventory().findItem(materialKey);
        if (item != null)
        {
            failWithMessage("Expected player '%s' to not have item '%s' in inventory, but it was found at slot %d.",
                actual.name(), materialKey, item.slot());
        }
        return this;
    }

    /**
     * Asserts that at least one received message contains the requested text fragment.
     *
     * @param expectedFragment
     *     Required message fragment.
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert receivedMessage(String expectedFragment)
    {
        final var actual = nonNullActual();
        Assertions
            .assertThat(actual.receivedMessagesText())
            .contains(expectedFragment);
        return this;
    }

    /**
     * Asserts that at least one captured chat component contains clickable text matching the fragment.
     *
     * @param expectedText
     *     Required text fragment that should be clickable.
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert hasClickableChatText(String expectedText)
    {
        final var actual = nonNullActual();
        int parseFailures = 0;
        boolean found = false;
        for (final var component : actual.chatComponents())
        {
            if (!component.json().contains(expectedText))
                continue;
            try
            {
                // findValue matches a clickEvent nested in an 'extra' child, not only the root object; NMS
                // serializes components that carry their clickable text in extra children.
                if (OBJECT_MAPPER.readTree(component.json()).findValue("clickEvent") != null)
                {
                    found = true;
                    break;
                }
            }
            catch (JacksonException ignored)
            {
                parseFailures++;
            }
        }
        if (!found)
        {
            // Malformed components counted here would otherwise silently read as "not clickable" and blame the
            // plugin under test; surface the count so the real cause is visible.
            final String parseNote = parseFailures > 0
                ? " (%d component(s) containing that text failed to parse as JSON)".formatted(parseFailures)
                : "";
            failWithMessage(
                "Expected player '%s' to have a clickable chat text matching '%s', but none was found.%s",
                actual.name(), expectedText, parseNote
            );
        }
        return this;
    }

    /**
     * Returns an AssertJ string assertion over all received messages concatenated into one text block.
     *
     * @return AssertJ string assertion.
     */
    public AbstractStringAssert<?> receivedMessagesText()
    {
        final var actual = nonNullActual();
        return Assertions.assertThat(actual.receivedMessagesText());
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // we call isNotNull() first, so actual is not null after that
    private PlayerHandle nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
