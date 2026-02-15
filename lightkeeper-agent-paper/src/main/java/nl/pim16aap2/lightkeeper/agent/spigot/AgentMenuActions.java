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

@SuppressWarnings({"deprecation", "UnstableApiUsage"})
final class AgentMenuActions
{
    private final AgentMainThreadExecutor mainThreadExecutor;
    private final AgentMenuController menuController;
    private final AgentSyntheticPlayerStore playerStore;
    private final ObjectMapper objectMapper;

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

    void openMainMenu(Player player)
    {
        menuController.openMainMenu(player);
    }

    void onInventoryClick(InventoryClickEvent event)
    {
        menuController.handleInventoryClick(event, playerStore::sendTrackedMessage);
    }

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

    private static boolean isActionableOpenInventory(InventoryView view)
    {
        return view.getType() != InventoryType.CRAFTING;
    }
}
