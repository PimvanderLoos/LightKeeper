package nl.pim16aap2.lightkeeper.agent.spigot;

import tools.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.CreatePlayer;
import nl.pim16aap2.lightkeeper.protocol.DropItem;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerChatComponents;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerInventory;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerMessages;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlock;
import nl.pim16aap2.lightkeeper.protocol.PlacePlayerBlock;
import nl.pim16aap2.lightkeeper.protocol.RightClickBlock;
import nl.pim16aap2.lightkeeper.protocol.TeleportPlayer;
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
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        assertThat(response.dispatched()).isTrue();
    }

    @Test
    void handleExecutePlayerCommand_shouldFallBackToServerDispatcherWhenPerformCommandReturnsFalse()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        when(player.performCommand("say hi")).thenReturn(false);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final ExecutePlayerCommand.Command command =
            new ExecutePlayerCommand.Command("request-cmd", uuid, "say hi");

        // execute
        final ExecutePlayerCommand.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.dispatchCommand(player, "say hi")).thenReturn(true);
            response = fixture.playerActions().handleExecutePlayerCommand(command);
        }

        // verify — a command performCommand cannot run still executes via the server dispatcher fallback
        assertThat(response.dispatched()).isTrue();
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
        assertThat(response.dropped()).isTrue();
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
        assertThat(response.dropped()).isFalse();
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

    @Test
    void handleGetPlayerMessages_shouldDrainAndReturnMessageHistory()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mockPlayer(uuid);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        when(fixture.nmsAdapter().drainReceivedMessages(uuid)).thenReturn(List.of("first", "second"));
        final GetPlayerMessages.Command command = new GetPlayerMessages.Command("request-messages", uuid);

        // execute
        final GetPlayerMessages.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleGetPlayerMessages(command);
        }

        // verify
        assertThat(response.messages()).containsExactly("first", "second");
    }

    @Test
    void handleGetPlayerChatComponents_shouldDrainAndReturnComponentHistory()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mockPlayer(uuid);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        when(fixture.nmsAdapter().drainChatComponents(uuid)).thenReturn(List.of("{\"text\":\"hello\"}"));
        final GetPlayerChatComponents.Command command =
            new GetPlayerChatComponents.Command("request-components", uuid);

        // execute
        final GetPlayerChatComponents.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleGetPlayerChatComponents(command);
        }

        // verify
        assertThat(response.componentsJson()).isEqualTo("[\"{\\\"text\\\":\\\"hello\\\"}\"]");
    }

    @Test
    void handleGetPlayerInventory_shouldSerializeNonAirItems()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mockPlayer(uuid);
        final PlayerInventory inventory = player.getInventory();
        final ItemStack stone = mock();
        when(stone.getType()).thenReturn(Material.STONE);
        when(stone.getItemMeta()).thenReturn(null);
        final ItemStack airItem = mock();
        when(airItem.getType()).thenReturn(Material.AIR);
        when(inventory.getContents()).thenReturn(new ItemStack[]{
            null,
            airItem,
            stone
        });
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final GetPlayerInventory.Command command = new GetPlayerInventory.Command("request-inventory", uuid);

        // execute
        final GetPlayerInventory.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleGetPlayerInventory(command);
        }

        // verify
        assertThat(response.inventoryJson())
            .contains("\"slot\":2")
            .contains("\"materialKey\":\"minecraft:stone\"")
            .contains("\"lore\":[]");
    }

    @Test
    void handleTeleportPlayer_shouldThrowWhenWorldNameIsBlank()
    {
        // setup
        final AgentPlayerActions playerActions = createPlayerActions();
        final TeleportPlayer.Command command =
            new TeleportPlayer.Command("request-tp-blank", UUID.randomUUID(), "   ", 0, 64, 0);

        // execute + verify
        assertThatThrownBy(() -> playerActions.handleTeleportPlayer(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("worldName");
    }

    @Test
    void handlePlacePlayerBlock_shouldThrowWhenMaterialIsUnknown()
    {
        // setup
        final AgentPlayerActions playerActions = createPlayerActions();
        final PlacePlayerBlock.Command command =
            new PlacePlayerBlock.Command("request-place", UUID.randomUUID(), "not_a_real_material", 1, 64, 2);

        // execute + verify
        try (MockedStatic<Material> materialMockedStatic = mockStatic(Material.class))
        {
            materialMockedStatic.when(() -> Material.matchMaterial(anyString(), eq(true))).thenReturn(null);
            assertThatThrownBy(() -> playerActions.handlePlacePlayerBlock(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown material");
        }
    }

    @Test
    void cleanupSyntheticPlayers_shouldRemoveAllRegisteredPlayers()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mockPlayer(uuid);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);

        // execute
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            fixture.playerActions().cleanupSyntheticPlayers();
        }

        // verify
        verify(fixture.nmsAdapter()).removePlayer(player);
        assertThat(fixture.playerStore().syntheticPlayerIds()).isEmpty();
    }

    @Test
    void cleanupSyntheticPlayers_shouldContinueCleanupWhenOnePlayerFails()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid1 = UUID.randomUUID();
        final UUID uuid2 = UUID.randomUUID();
        final Player player1 = mockPlayer(uuid1);
        final Player player2 = mockPlayer(uuid2);
        fixture.playerStore().registerSyntheticPlayer(uuid1, player1);
        fixture.playerStore().registerSyntheticPlayer(uuid2, player2);
        doThrow(new RuntimeException("removal failed")).when(fixture.nmsAdapter()).removePlayer(player1);

        // execute
        fixture.playerActions().cleanupSyntheticPlayers();

        // verify - player2 should still be attempted
        verify(fixture.nmsAdapter()).removePlayer(player2);
    }

    @Test
    void handleDropItem_shouldReturnNotDroppedWhenMainHandIsEmpty()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mockPlayer(uuid);
        final ItemStack emptyHand = mock();
        when(emptyHand.getType()).thenReturn(Material.AIR);
        when(player.getInventory().getItemInMainHand()).thenReturn(emptyHand);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final DropItem.Command command = new DropItem.Command("request-drop", uuid);

        // execute
        final DropItem.Response response;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleDropItem(command);
        }

        // verify - no item to drop, so dropped=false
        assertThat(response.dropped()).isFalse();
    }

    @Test
    void handleCreatePlayer_shouldSpawnPlayerWithCoordinatesAndOptionalPermissions()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final World world = mock();
        final Player player = mockPlayer(uuid);
        when(player.getName()).thenReturn("testbot");
        when(fixture.nmsAdapter().spawnPlayer(eq(uuid), eq("testbot"), eq(world), any())).thenReturn(player);
        final PermissionAttachment attachment = mock();
        when(player.addAttachment(any())).thenReturn(attachment);
        final CreatePlayer.Command command = new CreatePlayer.Command(
            "request-create", "testbot", uuid, "world", 10.0, 64.0, 20.0, null, "test.perm"
        );

        // execute
        final CreatePlayer.Response response;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = fixture.playerActions().handleCreatePlayer(command);
        }

        // verify
        assertThat(response.uuid()).isEqualTo(uuid);
        assertThat(response.name()).isEqualTo("testbot");
        // Regression (M1): the attachment must be stored on the player's state so it can be revoked later.
        // Production registers the player before applying permissions; if that order regresses, the attachment
        // is dropped and removePermissionAttachment finds nothing to detach.
        fixture.playerStore().removePermissionAttachment(uuid, player);
        verify(player).removeAttachment(attachment);
    }

    private static AgentPlayerActions createPlayerActions()
    {
        return createPlayerActionsFixture().playerActions();
    }

    private static PlayerActionsFixture createPlayerActionsFixture()
    {
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
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
        return new PlayerActionsFixture(playerActions, playerStore, nmsAdapter);
    }

    private static Player mockPlayer(UUID uuid)
    {
        final Player player = mock();
        final World world = mock();
        final Block block = mock();
        final PlayerInventory inventory = mock();
        final ItemStack item = mock();
        when(item.getType()).thenReturn(Material.STICK);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getWorld()).thenReturn(world);
        when(player.getInventory()).thenReturn(inventory);
        when(world.getBlockAt(1, 64, 2)).thenReturn(block);
        when(inventory.getItemInMainHand()).thenReturn(item);
        return player;
    }

    private record PlayerActionsFixture(
        AgentPlayerActions playerActions,
        AgentSyntheticPlayerStore playerStore,
        IBotPlayerNmsAdapter nmsAdapter)
    {
    }
}
