package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiConsumer;

final class AgentMenuController
{
    static final String MAIN_MENU_TITLE = "Main Menu";
    static final String SUB_MENU_TITLE = "Sub Menu";

    void openMainMenu(Player player)
    {
        final Inventory inventory = Bukkit.createInventory(player, 9, MAIN_MENU_TITLE);
        inventory.setItem(0, new ItemStack(Material.STONE));
        inventory.setItem(2, new ItemStack(Material.DIAMOND_SWORD));
        player.openInventory(inventory);
    }

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

    private void openSubMenu(Player player)
    {
        final Inventory inventory = Bukkit.createInventory(player, 9, SUB_MENU_TITLE);
        inventory.setItem(0, new ItemStack(Material.BARRIER));
        inventory.setItem(2, new ItemStack(Material.DIAMOND_SWORD));
        player.openInventory(inventory);
    }
}
