package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Assertions for synthetic player state.
 */
public final class PlayerHandleAssert extends AbstractAssert<PlayerHandleAssert, @Nullable PlayerHandle>
{
    PlayerHandleAssert(@Nullable PlayerHandle actual)
    {
        super(actual, PlayerHandleAssert.class);
    }

    /** @return Frozen assertions over the current inventory; recreate navigation when retrying. */
    public InventoryAssert inventory()
    {
        return extracting(PlayerHandle::inventory, InventoryAssert::new);
    }

    /** @return Frozen individual-message assertions for exact contents, order, counts and predicates. */
    public ListAssert<String> receivedMessages()
    {
        return extracting(PlayerHandle::receivedMessages, Assertions::assertThat);
    }

    /** @return Frozen component-list assertions; recreate navigation inside retries. */
    public ListAssert<ChatComponentSnapshot> chatComponents()
    {
        return extracting(PlayerHandle::chatComponents, Assertions::assertThat);
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
     * Asserts that the player's current effective Bukkit permissions grant the node.
     *
     * @param permission
     *     Permission node; must not be blank.
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert hasPermission(String permission)
    {
        return hasPermissionState(permission, true);
    }

    /**
     * Asserts that the player's current effective Bukkit permissions do not grant the node.
     *
     * @param permission
     *     Permission node; must not be blank.
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert doesNotHavePermission(String permission)
    {
        return hasPermissionState(permission, false);
    }

    /**
     * Asserts that the player currently has an actionable menu open.
     *
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert hasOpenMenu()
    {
        return hasOpenMenuState(true);
    }

    /**
     * Asserts that the player currently has no actionable menu open, without waiting.
     *
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert doesNotHaveOpenMenu()
    {
        return hasOpenMenuState(false);
    }

    private PlayerHandleAssert hasPermissionState(String permission, boolean expected)
    {
        Objects.requireNonNull(permission, "permission may not be null.");
        if (permission.isBlank())
            throw new IllegalArgumentException("permission may not be blank.");
        final var actual = nonNullActual();
        final boolean observed = actual.permissions().has(permission);
        if (observed != expected)
        {
            failWithMessage(
                "Expected player '%s' to %s permission '%s', but effective permission was %s.",
                actual.name(),
                expected ? "have" : "not have",
                permission,
                observed
            );
        }
        return this;
    }

    private PlayerHandleAssert hasOpenMenuState(boolean expected)
    {
        final var actual = nonNullActual();
        final boolean observed = actual.getMenu() != null;
        if (observed != expected)
        {
            failWithMessage(
                "Expected player '%s' to have %s, but %s.",
                actual.name(),
                expected ? "an open menu" : "no open menu",
                observed ? "a menu was open" : "no menu was open"
            );
        }
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
        Objects.requireNonNull(expectedText, "expectedText may not be null.");
        if (expectedText.isEmpty())
            throw new IllegalArgumentException("expectedText may not be empty.");
        final var actual = nonNullActual();
        int parseFailures = 0;
        boolean found = false;
        for (final var component : actual.chatComponents())
        {
            try
            {
                if (component.hasClickableText(expectedText))
                {
                    found = true;
                    break;
                }
            }
            catch (IllegalArgumentException malformed)
            {
                parseFailures++;
            }
        }
        if (!found)
        {
            final String parseNote = parseFailures > 0
                ? " (%d component(s) failed to parse as JSON)".formatted(parseFailures) : "";
            failWithMessage(
                "Expected player '%s' to have a clickable chat text matching '%s', but none was found.%s",
                actual.name(),
                expectedText,
                parseNote
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
