package nl.pim16aap2.lightkeeper.agent.spigot;

import tools.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.DropItem;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlock;
import nl.pim16aap2.lightkeeper.protocol.RightClickBlock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentPlayerActionsTest
{
    @Test
    void handleExecutePlayerCommand_shouldStripLeadingSlashFromCommand()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        when(player.performCommand("gamemode creative")).thenReturn(true);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final ExecutePlayerCommand.Command command =
            new ExecutePlayerCommand.Command("request-cmd", uuid, "/gamemode creative");

        // execute
        final ExecutePlayerCommand.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleExecutePlayerCommand(command);
        }

        // verify
        assertThat(response.requestId()).isEqualTo("request-cmd");
        assertThat(response.success()).isTrue();
    }

    @Test
    void handleLeftClickBlock_shouldFirePlayerInteractEvent()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mockPlayer(uuid);
        final PluginManager pluginManager = mock();
        final ArgumentCaptor<PlayerInteractEvent> eventCaptor = ArgumentCaptor.forClass(PlayerInteractEvent.class);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final LeftClickBlock.Command command =
            new LeftClickBlock.Command("request-click", uuid, 1, 64, 2, "NORTH");

        // execute
        final LeftClickBlock.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            response = fixture.playerActions().handleLeftClickBlock(command);
        }

        // verify
        assertThat(response.requestId()).isEqualTo("request-click");
        assertThat(response.cancelled()).isFalse();
        verify(pluginManager).callEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAction()).isEqualTo(Action.LEFT_CLICK_BLOCK);
        assertThat(eventCaptor.getValue().getBlockFace()).isEqualTo(BlockFace.NORTH);
        assertThat(eventCaptor.getValue().getPlayer()).isSameAs(player);
    }

    @Test
    void handleDropItem_shouldKeepEntityAndConsumeInventoryWhenEventNotCancelled()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final ItemStack item = mock();
        final Material nonAirMaterial = mock(Material.class);
        final Player player = mock();
        final World world = mock();
        final org.bukkit.entity.Item entity = mock();
        final PlayerInventory inventory = mock();
        final PluginManager pluginManager = mock();
        when(nonAirMaterial.isAir()).thenReturn(false);
        when(item.getType()).thenReturn(nonAirMaterial);
        when(item.getAmount()).thenReturn(3);
        when(item.clone()).thenReturn(item);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(world.dropItemNaturally(any(), any())).thenReturn(entity);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final DropItem.Command command = new DropItem.Command("req-drop", uuid);

        // execute
        final DropItem.Response response;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            response = fixture.playerActions().handleDropItem(command);
        }

        // verify
        assertThat(response.requestId()).isEqualTo("req-drop");
        assertThat(response.eventCancelled()).isFalse();
        verify(entity, never()).remove();
        verify(item).setAmount(2);
        verify(inventory).setItemInMainHand(item);
    }

    @Test
    void handleDropItem_shouldRemoveEntityAndLeaveInventoryWhenEventCancelled()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final ItemStack item = mock();
        final Material nonAirMaterial = mock(Material.class);
        final Player player = mock();
        final World world = mock();
        final org.bukkit.entity.Item entity = mock();
        final PlayerInventory inventory = mock();
        final PluginManager pluginManager = mock();
        when(nonAirMaterial.isAir()).thenReturn(false);
        when(item.getType()).thenReturn(nonAirMaterial);
        when(item.clone()).thenReturn(item);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(world.dropItemNaturally(any(), any())).thenReturn(entity);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final DropItem.Command command = new DropItem.Command("req-drop-cancel", uuid);

        // execute
        final DropItem.Response response;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            doAnswer(inv ->
            {
                final PlayerDropItemEvent event = inv.getArgument(0);
                event.setCancelled(true);
                return null;
            }).when(pluginManager).callEvent(any(PlayerDropItemEvent.class));
            response = fixture.playerActions().handleDropItem(command);
        }

        // verify
        assertThat(response.requestId()).isEqualTo("req-drop-cancel");
        assertThat(response.eventCancelled()).isTrue();
        verify(entity).remove();
        verify(inventory, never()).setItemInMainHand(any());
    }

    @Test
    void handleRightClickBlock_shouldThrowWhenBlockFaceIsUnknown()
        throws Exception
    {
        // setup
        final AgentPlayerActions playerActions = createPlayerActions();
        final RightClickBlock.Command command =
            new RightClickBlock.Command("request-click", UUID.randomUUID(), 1, 64, 2, "not-a-face");

        // execute + verify
        assertThatThrownBy(() -> playerActions.handleRightClickBlock(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("block face");
    }

    private static AgentPlayerActions createPlayerActions()
    {
        return createPlayerActionsFixture().playerActions();
    }

    private static PlayerActionsFixture createPlayerActionsFixture()
    {
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        final AgentSyntheticPlayerStore playerStore = new AgentSyntheticPlayerStore();
        final ObjectMapper objectMapper = new ObjectMapper();
        final IBotPlayerNmsAdapter nmsAdapter = mock();

        final AgentPlayerActions playerActions = new AgentPlayerActions(
            plugin,
            mainThreadExecutor,
            playerStore,
            objectMapper,
            nmsAdapter
        );
        return new PlayerActionsFixture(playerActions, playerStore);
    }

    private static Player mockPlayer(UUID uuid)
    {
        final Player player = mock();
        final World world = mock();
        final Block block = mock();
        final PlayerInventory inventory = mock();
        final ItemStack item = new ItemStack(Material.STICK);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getWorld()).thenReturn(world);
        when(player.getInventory()).thenReturn(inventory);
        when(world.getBlockAt(1, 64, 2)).thenReturn(block);
        when(inventory.getItemInMainHand()).thenReturn(item);
        return player;
    }

    private record PlayerActionsFixture(
        AgentPlayerActions playerActions,
        AgentSyntheticPlayerStore playerStore)
    {
    }
}
