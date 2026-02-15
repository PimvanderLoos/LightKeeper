package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Protocol action handler for inventory/menu-related agent actions.
 *
 * <p>This class provides the executable behavior behind menu protocol calls such as opening menus, reading
 * open inventory state, and simulating click/drag interactions.
 */
@SuppressWarnings({"deprecation", "UnstableApiUsage"})
final class AgentMenuActions
{
    /**
     * Scheduler bridge for Bukkit main-thread execution.
     */
    private final AgentMainThreadExecutor mainThreadExecutor;
    /**
     * Deterministic controller for menu structure and click outcomes.
     */
    private final AgentMenuController menuController;
    /**
     * Synthetic player lookup and tracked message persistence.
     */
    private final AgentSyntheticPlayerStore playerStore;
    /**
     * JSON mapper used to serialize structured inventory item payloads.
     */
    private final ObjectMapper objectMapper;

    /**
     * @param mainThreadExecutor
     *     Main-thread executor for Bukkit-safe menu interactions.
     * @param menuController
     *     Controller that defines menu structure and click routing.
     * @param playerStore
     *     Synthetic player registry used to resolve protocol UUIDs.
     * @param objectMapper
     *     JSON serializer for response payloads.
     */
    AgentMenuActions(
        AgentMainThreadExecutor mainThreadExecutor,
        AgentMenuController menuController,
        AgentSyntheticPlayerStore playerStore,
        ObjectMapper objectMapper)
    {
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.menuController = Objects.requireNonNull(menuController, "menuController");
        this.playerStore = Objects.requireNonNull(playerStore, "playerStore");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Opens the main test menu for the provided player.
     *
     * @param player
     *     Player who should receive the menu.
     */
    void openMainMenu(Player player)
    {
        menuController.openMainMenu(player);
    }

    /**
     * Processes inventory click events for managed test menus.
     *
     * @param event
     *     Inventory click event fired by Bukkit.
     */
    void onInventoryClick(InventoryClickEvent event)
    {
        menuController.handleInventoryClick(event, playerStore::sendTrackedMessage);
    }

    /**
     * Handles {@code GET_OPEN_MENU} by returning title and non-air slot metadata for the active inventory.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; requires {@code uuid}.
     * @return
     *     Success response with menu state or {@code open=false} when no actionable menu is open.
     * @throws Exception
     *     Propagates parsing and Bukkit execution failures.
     */
    AgentResponse handleGetOpenMenu(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final Map<String, String> data = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final InventoryView view = player.getOpenInventory();

            if (!isActionableOpenInventory(view))
                return Map.of("open", "false");

            final List<Map<String, @Nullable Object>> items = new ArrayList<>();
            for (int rawSlot = 0; rawSlot < view.countSlots(); ++rawSlot)
            {
                final ItemStack item = view.getItem(rawSlot);
                if (item == null || item.getType().isAir())
                    continue;

                final Map<String, @Nullable Object> itemData = new HashMap<>();
                itemData.put("slot", rawSlot);
                itemData.put("materialKey", item.getType().getKey().toString());
                itemData.put("displayName", item.getItemMeta() == null ? null : item.getItemMeta().getDisplayName());
                itemData.put(
                    "lore",
                    item.getItemMeta() == null
                        ? List.of()
                        : Objects.requireNonNullElse(item.getItemMeta().getLore(), List.of())
                );
                items.add(itemData);
            }

            return Map.of(
                "open", "true",
                "title", view.getTitle(),
                "itemsJson", objectMapper.writeValueAsString(items)
            );
        });

        return AgentResponses.successResponse(requestId, data);
    }

    /**
     * Handles {@code CLICK_MENU_SLOT} by synthesizing and dispatching an inventory click event.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; requires {@code uuid} and non-negative {@code slot}.
     * @return
     *     Success or validation error response.
     * @throws Exception
     *     Propagates parsing and Bukkit execution failures.
     */
    AgentResponse handleClickMenuSlot(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final int slot = AgentRequestParsers.parseInt(arguments.getOrDefault("slot", "-1"));
        if (slot < 0)
        {
            return AgentResponses.errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'slot' must be >= 0.");
        }

        mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final InventoryView view = player.getOpenInventory();
            if (!isActionableOpenInventory(view))
                throw new IllegalStateException("Player does not have an actionable open menu.");

            final InventoryClickEvent event = new InventoryClickEvent(
                view,
                view.getSlotType(slot),
                slot,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
            );
            Bukkit.getPluginManager().callEvent(event);
            return Boolean.TRUE;
        });

        return AgentResponses.successResponse(requestId, Map.of("clicked", "true"));
    }

    /**
     * Handles {@code DRAG_MENU_SLOTS} by creating and dispatching an inventory drag event.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; requires {@code uuid}, {@code material}, and comma-separated {@code slots}.
     * @return
     *     Success or validation error response.
     * @throws Exception
     *     Propagates parsing and Bukkit execution failures.
     */
    AgentResponse handleDragMenuSlots(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final String materialName = arguments.getOrDefault("material", "").trim();
        final Material material = AgentRequestParsers.parseMaterial(materialName);
        if (material == null)
        {
            return AgentResponses.errorResponse(
                requestId,
                "INVALID_ARGUMENT",
                "Unknown material '%s'.".formatted(materialName)
            );
        }

        final String slots = arguments.getOrDefault("slots", "").trim();
        if (slots.isBlank())
        {
            return AgentResponses.errorResponse(
                requestId,
                "INVALID_ARGUMENT",
                "Argument 'slots' must not be blank."
            );
        }

        final List<Integer> rawSlots = Arrays.stream(slots.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(Integer::parseInt)
            .toList();

        mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final InventoryView view = player.getOpenInventory();
            if (!isActionableOpenInventory(view))
                throw new IllegalStateException("Player does not have an actionable open menu.");

            final ItemStack cursorItem = new ItemStack(material);
            final Map<Integer, ItemStack> newItems = new HashMap<>();
            for (final Integer rawSlot : rawSlots)
                newItems.put(rawSlot, cursorItem.clone());

            final InventoryDragEvent event = new InventoryDragEvent(
                view,
                cursorItem.clone(),
                cursorItem,
                true,
                newItems
            );
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled())
            {
                for (final Map.Entry<Integer, ItemStack> entry : newItems.entrySet())
                    view.setItem(entry.getKey(), entry.getValue());
            }
            return Boolean.TRUE;
        });

        return AgentResponses.successResponse(requestId, Map.of("dragged", "true"));
    }

    /**
     * Determines whether the currently open inventory should be considered actionable by agent menu commands.
     *
     * @param view
     *     Active inventory view.
     * @return
     *     {@code true} when the inventory can be interacted with as a menu action target.
     */
    private static boolean isActionableOpenInventory(InventoryView view)
    {
        return view.getType() != InventoryType.CRAFTING;
    }
}
