package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.DropItem;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerChatComponents;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerInventory;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerMessages;
import nl.pim16aap2.lightkeeper.protocol.ItemSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Protocol action handler for synthetic-player messages, inventory snapshots, and item drops.
 */
final class AgentPlayerStateActions
{
    private final AgentMainThreadExecutor mainThreadExecutor;
    private final AgentSyntheticPlayerStore playerStore;
    private final ObjectMapper objectMapper;
    private final IBotPlayerNmsAdapter botPlayerNmsAdapter;

    /**
     * @param mainThreadExecutor
     *     Main-thread execution bridge for Bukkit-safe operations.
     * @param playerStore
     *     Registry containing synthetic players and their captured state.
     * @param objectMapper
     *     JSON serializer for captured chat components.
     * @param botPlayerNmsAdapter
     *     NMS adapter used to drain player messages and chat components.
     */
    AgentPlayerStateActions(
        AgentMainThreadExecutor mainThreadExecutor,
        AgentSyntheticPlayerStore playerStore,
        ObjectMapper objectMapper,
        IBotPlayerNmsAdapter botPlayerNmsAdapter)
    {
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.playerStore = Objects.requireNonNull(playerStore, "playerStore");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.botPlayerNmsAdapter = Objects.requireNonNull(botPlayerNmsAdapter, "botPlayerNmsAdapter");
    }

    GetPlayerMessages.Response handleGetPlayerMessages(GetPlayerMessages.Command command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final List<String> messages = mainThreadExecutor.callOnMainThread(() ->
        {
            playerStore.getRequiredPlayer(uuid);
            playerStore.capturePlayerMessages(botPlayerNmsAdapter, uuid);
            return playerStore.getPlayerMessages(uuid);
        });
        return new GetPlayerMessages.Response(messages);
    }

    GetPlayerChatComponents.Response handleGetPlayerChatComponents(GetPlayerChatComponents.Command command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String componentsJson = mainThreadExecutor.callOnMainThread(() ->
        {
            playerStore.getRequiredPlayer(uuid);
            playerStore.capturePlayerChatComponents(botPlayerNmsAdapter, uuid);
            return objectMapper.writeValueAsString(playerStore.getPlayerChatComponents(uuid));
        });
        return new GetPlayerChatComponents.Response(componentsJson);
    }

    GetPlayerInventory.Response handleGetPlayerInventory(GetPlayerInventory.Command command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final List<ItemSnapshot> items = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            return buildInventoryItems(player.getInventory().getContents());
        });
        return new GetPlayerInventory.Response(items);
    }

    private static List<ItemSnapshot> buildInventoryItems(ItemStack... contents)
    {
        final List<ItemSnapshot> items = new ArrayList<>();
        for (int i = 0; i < contents.length; i++)
        {
            final ItemStack item = contents[i];
            if (item == null || AgentMaterials.isAir(item.getType()))
                continue;
            items.add(AgentItemSnapshots.of(i, item));
        }
        return items;
    }

    DropItem.Response handleDropItem(DropItem.Command command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final Boolean dropped = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || AgentMaterials.isAir(item.getType()))
                return Boolean.FALSE;

            final ItemStack singleItem = item.clone();
            singleItem.setAmount(1);
            final Item droppedItem = player.getWorld().dropItemNaturally(player.getLocation(), singleItem);
            final PlayerDropItemEvent event = new PlayerDropItemEvent(player, droppedItem);
            Bukkit.getPluginManager().callEvent(event);

            if (event.isCancelled())
            {
                droppedItem.remove();
                return Boolean.FALSE;
            }

            if (item.getAmount() > 1)
            {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItemInMainHand(item);
            }
            else
            {
                player.getInventory().setItemInMainHand(null);
            }
            return Boolean.TRUE;
        });
        return new DropItem.Response(dropped);
    }
}
