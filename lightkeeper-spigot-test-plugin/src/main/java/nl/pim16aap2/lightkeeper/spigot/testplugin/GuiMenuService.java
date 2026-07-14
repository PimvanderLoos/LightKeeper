package nl.pim16aap2.lightkeeper.spigot.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds and opens deterministic test menus for the standalone integration plugin.
 */
final class GuiMenuService
{
    static final String MAIN_MENU_TITLE = "Main Menu";
    static final String SUB_MENU_TITLE = "Sub Menu";
    static final String BUTTON_NAME = "Button 1";
    static final String BUTTON_CLICK_MESSAGE = "You clicked Button 1";

    /**
     * Opens the main menu for the provided player.
     *
     * @param player
     *     Player for whom to open the menu.
     */
    void openMainMenu(Player player)
    {
        final Inventory inventory = Bukkit.createInventory(player, 9, MAIN_MENU_TITLE);
        inventory.setItem(0, namedItem(new ItemStack(Material.STONE), BUTTON_NAME));
        inventory.setItem(2, new ItemStack(Material.DIAMOND_SWORD));
        player.openInventory(inventory);
    }

    /**
     * Applies a display name to an item so display-name-based menu interactions can be integration-tested.
     *
     * @param item
     *     Item to name.
     * @param displayName
     *     Display name to apply.
     * @return The same item, with the display name applied when item meta is available.
     */
    private static ItemStack namedItem(ItemStack item, String displayName)
    {
        final ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null)
        {
            itemMeta.setDisplayName(displayName);
            item.setItemMeta(itemMeta);
        }
        return item;
    }

    /**
     * Opens the sub menu for the provided player.
     *
     * @param player
     *     Player for whom to open the menu.
     */
    void openSubMenu(Player player)
    {
        final Inventory inventory = Bukkit.createInventory(player, 9, SUB_MENU_TITLE);
        inventory.setItem(0, new ItemStack(Material.BARRIER));
        inventory.setItem(2, new ItemStack(Material.DIAMOND_SWORD));
        player.openInventory(inventory);
    }

    /**
     * Determines whether a menu title belongs to this plugin's managed GUI flow.
     *
     * @param title
     *     Inventory title to evaluate.
     * @return
     *     {@code true} when the title is one of the plugin-owned menu titles.
     */
    static boolean isManagedMenuTitle(String title)
    {
        return MAIN_MENU_TITLE.equals(title) || SUB_MENU_TITLE.equals(title);
    }
}
