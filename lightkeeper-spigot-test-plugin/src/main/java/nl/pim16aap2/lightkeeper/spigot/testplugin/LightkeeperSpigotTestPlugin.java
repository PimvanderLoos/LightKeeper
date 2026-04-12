package nl.pim16aap2.lightkeeper.spigot.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entrypoint for the standalone Spigot test plugin used by integration tests.
 *
 * <p>The plugin exposes the {@code /lktestgui} command and opens InventoryGUI-based menus that can be controlled
 * by a player and through the LightKeeper runtime protocol.</p>
 */
public final class LightkeeperSpigotTestPlugin extends JavaPlugin implements Listener
{
    /**
     * Canonical command name provided by this plugin.
     */
    private static final String COMMAND_NAME = "lktestgui";
    /**
     * Prefix used for deterministic block interaction messages.
     */
    static final String BLOCK_CLICK_MESSAGE_PREFIX = "LK_BLOCK_CLICK";
    /**
     * Menu service that owns deterministic test GUI construction and transitions.
     */
    private final GuiMenuService guiMenuService = new GuiMenuService();

    /**
     * Wires command handling and event listeners.
     */
    @Override
    public void onEnable()
    {
        final PluginCommand pluginCommand = getCommand(COMMAND_NAME);
        if (pluginCommand == null)
            throw new IllegalStateException(
                "Required command '/%s' is not declared in plugin metadata.".formatted(COMMAND_NAME));

        pluginCommand.setExecutor(new LkTestGuiCommandExecutor(guiMenuService));
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    /**
     * Handles clicks in managed menus deterministically so RPC-simulated clicks match direct player interactions.
     *
     * @param event
     *     Inventory click event fired by Bukkit.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event)
    {
        final String title = event.getView().getTitle();
        if (!GuiMenuService.isManagedMenuTitle(title))
            return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (GuiMenuService.MAIN_MENU_TITLE.equals(title) && event.getRawSlot() == 0)
        {
            player.sendMessage(GuiMenuService.BUTTON_CLICK_MESSAGE);
            guiMenuService.openSubMenu(player);
            return;
        }

        if (GuiMenuService.SUB_MENU_TITLE.equals(title) && event.getRawSlot() == 0)
        {
            guiMenuService.openMainMenu(player);
            return;
        }

        if (GuiMenuService.SUB_MENU_TITLE.equals(title) && event.getRawSlot() == 2)
            player.closeInventory();
    }

    /**
     * Ensures drag interactions inside test menus remain writable for functional tests.
     *
     * @param event
     *     Inventory drag event fired by Bukkit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event)
    {
        if (GuiMenuService.isManagedMenuTitle(event.getView().getTitle()))
            event.setCancelled(false);
    }

    /**
     * Records block click interactions so integration tests can prove synthetic block clicks fire real Bukkit events.
     *
     * @param event
     *     Player interaction event fired by Bukkit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        final Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null)
            return;

        event.getPlayer().sendMessage(
            "%s action=%s block=%s x=%d y=%d z=%d face=%s item=%s"
                .formatted(
                    BLOCK_CLICK_MESSAGE_PREFIX,
                    event.getAction(),
                    clickedBlock.getType().getKey(),
                    clickedBlock.getX(),
                    clickedBlock.getY(),
                    clickedBlock.getZ(),
                    event.getBlockFace(),
                    itemKey(event.getItem())
                )
        );
    }

    private static NamespacedKey itemKey(ItemStack itemStack)
    {
        if (itemStack == null)
            return Material.AIR.getKey();
        return itemStack.getType().getKey();
    }
}
