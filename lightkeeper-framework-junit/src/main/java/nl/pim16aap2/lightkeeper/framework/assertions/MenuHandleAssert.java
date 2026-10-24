package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Assertions for open menu state.
 */
public final class MenuHandleAssert extends AbstractAssert<MenuHandleAssert, @Nullable MenuHandle>
{
    MenuHandleAssert(@Nullable MenuHandle actual)
    {
        super(actual, MenuHandleAssert.class);
    }

    /**
     * Asserts that this retained handle currently refers to an open menu, without waiting.
     *
     * @return This assertion for fluent chaining.
     */
    public MenuHandleAssert isOpen()
    {
        return hasOpenState(true);
    }

    /**
     * Asserts that the menu for this retained handle is currently closed, without waiting.
     *
     * <p>A null handle fails this assertion. Use {@link PlayerHandleAssert#doesNotHaveOpenMenu()} when the
     * player has no menu handle.
     *
     * @return This assertion for fluent chaining.
     */
    public MenuHandleAssert isClosed()
    {
        return hasOpenState(false);
    }

    private MenuHandleAssert hasOpenState(boolean expected)
    {
        final var actual = nonNullActual();
        final MenuSnapshot snapshot = actual.snapshot();
        if (snapshot.open() != expected)
        {
            failWithMessage("Expected menu for player '%s' to be %s, but it was %s (title='%s').",
                actual.player().name(), expected ? "open" : "closed",
                snapshot.open() ? "open" : "closed", snapshot.title());
        }
        return this;
    }

    /**
     * Asserts that an open menu exists and has the requested title.
     *
     * @param expectedTitle
     *     Expected menu title.
     * @return This assertion for fluent chaining.
     */
    public MenuHandleAssert hasTitle(String expectedTitle)
    {
        final var actual = nonNullActual();
        if (!actual.hasTitle(expectedTitle))
        {
            failWithMessage("Expected open menu title '%s'.", expectedTitle);
        }
        return this;
    }

    /**
     * Asserts that a menu slot contains the requested material.
     *
     * @param slot
     *     Slot index.
     * @param materialKey
     *     Expected material key.
     * @return This assertion for fluent chaining.
     */
    public MenuHandleAssert hasItemAt(int slot, String materialKey)
    {
        final var actual = nonNullActual();
        if (!actual.hasItemAt(slot, materialKey))
        {
            failWithMessage("Expected menu item at slot %d with material '%s'.", slot, materialKey);
        }
        return this;
    }

    /**
     * Asserts that a menu slot matches the requested Bukkit item stack snapshot.
     *
     * @param slot
     *     Slot index.
     * @param itemStack
     *     Expected item stack.
     * @return This assertion for fluent chaining.
     */
    public MenuHandleAssert hasItemAt(int slot, ItemStack itemStack)
    {
        final var actual = nonNullActual();
        if (!actual.hasItemAt(slot, itemStack))
        {
            failWithMessage("Expected menu item at slot %d with expected ItemStack.", slot);
        }
        return this;
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // we call isNotNull() first, so actual is not null after that
    private MenuHandle nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
