package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import org.assertj.core.api.AbstractAssert;
import org.bukkit.inventory.ItemStack;

/**
 * Assertions for open menu state.
 */
public final class MenuHandleAssert extends AbstractAssert<MenuHandleAssert, MenuHandle>
{
    MenuHandleAssert(MenuHandle actual)
    {
        super(actual, MenuHandleAssert.class);
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
        isNotNull();
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
        isNotNull();
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
        isNotNull();
        if (!actual.hasItemAt(slot, itemStack))
        {
            failWithMessage("Expected menu item at slot %d with expected ItemStack.", slot);
        }
        return this;
    }
}
