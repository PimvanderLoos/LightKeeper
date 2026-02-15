package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiConsumer;

/**
 * Defines and controls the built-in test inventories used by the agent protocol.
 *
 * <p>This class is intentionally deterministic: menu layout and click behavior are fixed so integration tests can
 * assert inventory state transitions and tracked messages.
 */
final class AgentMenuController
{
    /**
     * Title of the top-level menu opened for the {@code lktestgui} flow.
     */
    static final String MAIN_MENU_TITLE = "Main Menu";
    /**
     * Title of the secondary menu reached from the main menu.
     */
    static final String SUB_MENU_TITLE = "Sub Menu";

    /**
     * Opens the canonical main test menu for the provided player.
     *
     * @param player
     *     Player for whom to open the inventory.
     */
    void openMainMenu(Player player)
    {
        final Inventory inventory = Bukkit.createInventory(player, 9, MAIN_MENU_TITLE);
        inventory.setItem(0, new ItemStack(Material.STONE));
        inventory.setItem(2, new ItemStack(Material.DIAMOND_SWORD));
        player.openInventory(inventory);
    }

    /**
     * Applies deterministic click handling for the built-in test menus.
     *
     * @param event
     *     Bukkit click event to process.
     * @param messageTracker
     *     Callback used to send and record menu messages.
     * @return
     *     {@code true} when the click was in one of the managed test menus, {@code false} otherwise.
     */
    boolean handleInventoryClick(InventoryClickEvent event, BiConsumer<Player, String> messageTracker)
    {
        final InventoryView view = event.getView();
        final String title = view.getTitle();
        if (!MAIN_MENU_TITLE.equals(title) && !SUB_MENU_TITLE.equals(title))
            return false;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player))
            return true;

        if (MAIN_MENU_TITLE.equals(title) && event.getRawSlot() == 0)
        {
            messageTracker.accept(player, "You clicked Button 1");
            openSubMenu(player);
            return true;
        }

        if (SUB_MENU_TITLE.equals(title) && event.getRawSlot() == 0)
        {
            openMainMenu(player);
            return true;
        }

        if (SUB_MENU_TITLE.equals(title) && event.getRawSlot() == 2)
            player.closeInventory();

        return true;
    }

    /**
     * Opens the canonical sub menu for the provided player.
     *
     * @param player
     *     Player for whom to open the sub menu.
     */
    private void openSubMenu(Player player)
    {
        final Inventory inventory = Bukkit.createInventory(player, 9, SUB_MENU_TITLE);
        inventory.setItem(0, new ItemStack(Material.BARRIER));
        inventory.setItem(2, new ItemStack(Material.DIAMOND_SWORD));
        player.openInventory(inventory);
    }
}
