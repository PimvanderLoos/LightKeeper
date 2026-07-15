package nl.pim16aap2.lightkeeper.agent.spigot;

import tools.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.BotJoinPhase;
import nl.pim16aap2.lightkeeper.nms.api.IBotLoginDriver;
import nl.pim16aap2.lightkeeper.nms.api.IBotLoginOutcome;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import nl.pim16aap2.lightkeeper.protocol.CreatePlayer;
import nl.pim16aap2.lightkeeper.protocol.DropItem;
import nl.pim16aap2.lightkeeper.protocol.DropResult;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerChatComponents;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerInventory;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerMessages;
import nl.pim16aap2.lightkeeper.protocol.HasPlayerPermission;
import nl.pim16aap2.lightkeeper.protocol.JoinMode;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlock;
import nl.pim16aap2.lightkeeper.protocol.MutatePlayerPermission;
import nl.pim16aap2.lightkeeper.protocol.PlacePlayerBlock;
import nl.pim16aap2.lightkeeper.protocol.PlayerChat;
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
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

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
            response = fixture.stateActions().handleDropItem(command);
        }

        // verify
        assertThat(response.result()).isEqualTo(DropResult.DROPPED);
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
            response = fixture.stateActions().handleDropItem(command);
        }

        // verify
        assertThat(response.result()).isEqualTo(DropResult.CANCELLED);
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
            response = fixture.stateActions().handleGetPlayerMessages(command);
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
            response = fixture.stateActions().handleGetPlayerChatComponents(command);
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
            response = fixture.stateActions().handleGetPlayerInventory(command);
        }

        // verify
        assertThat(response.items()).singleElement().satisfies(item ->
        {
            assertThat(item.slot()).isEqualTo(2);
            assertThat(item.materialKey()).isEqualTo("minecraft:stone");
            assertThat(item.lore()).isEmpty();
        });
    }

    @Test
    void handleMutatePlayerPermission_shouldGrantPermissionWhenModeIsGrant()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        final PermissionAttachment attachment = mock();
        when(player.addAttachment(any())).thenReturn(attachment);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final MutatePlayerPermission.Command command = new MutatePlayerPermission.Command(
            "request-grant", uuid, "test.perm", MutatePlayerPermission.Mode.GRANT);

        // execute
        final MutatePlayerPermission.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleMutatePlayerPermission(command);
        }

        // verify
        assertThat(response).isNotNull();
        verify(attachment).setPermission("test.perm", true);
    }

    @Test
    void handleMutatePlayerPermission_shouldRevokePermissionWhenModeIsRevoke()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        final PermissionAttachment attachment = mock();
        when(player.addAttachment(any())).thenReturn(attachment);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final MutatePlayerPermission.Command command = new MutatePlayerPermission.Command(
            "request-revoke", uuid, "test.perm", MutatePlayerPermission.Mode.REVOKE);

        // execute
        final MutatePlayerPermission.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleMutatePlayerPermission(command);
        }

        // verify
        assertThat(response).isNotNull();
        verify(attachment).setPermission("test.perm", false);
    }

    @Test
    void handleMutatePlayerPermission_shouldUnsetPermissionWhenModeIsUnset()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        final PermissionAttachment attachment = mock();
        when(player.addAttachment(any())).thenReturn(attachment);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final MutatePlayerPermission.Command grantCommand = new MutatePlayerPermission.Command(
            "request-grant", uuid, "test.perm", MutatePlayerPermission.Mode.GRANT);
        final MutatePlayerPermission.Command unsetCommand = new MutatePlayerPermission.Command(
            "request-unset", uuid, "test.perm", MutatePlayerPermission.Mode.UNSET);

        // execute
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            fixture.playerActions().handleMutatePlayerPermission(grantCommand);
            fixture.playerActions().handleMutatePlayerPermission(unsetCommand);
        }

        // verify
        verify(attachment).unsetPermission("test.perm");
    }

    @Test
    void handleHasPlayerPermission_shouldReturnTrueWhenPlayerHasPermission()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        when(player.hasPermission("test.perm")).thenReturn(true);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final HasPlayerPermission.Command command =
            new HasPlayerPermission.Command("request-has-true", uuid, "test.perm");

        // execute
        final HasPlayerPermission.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleHasPlayerPermission(command);
        }

        // verify
        assertThat(response.value()).isTrue();
    }

    @Test
    void handleHasPlayerPermission_shouldReturnFalseWhenPlayerLacksPermission()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        when(player.hasPermission("test.perm")).thenReturn(false);
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final HasPlayerPermission.Command command =
            new HasPlayerPermission.Command("request-has-false", uuid, "test.perm");

        // execute
        final HasPlayerPermission.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleHasPlayerPermission(command);
        }

        // verify
        assertThat(response.value()).isFalse();
    }

    @Test
    void teleportPlayerCommand_shouldRejectBlankWorldName()
    {
        // setup + execute + verify — validation is enforced by the command's compact constructor
        assertThatThrownBy(() ->
            new TeleportPlayer.Command("request-tp-blank", UUID.randomUUID(), "   ", 0, 64, 0))
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
            response = fixture.stateActions().handleDropItem(command);
        }

        // verify - no item to drop, so the empty hand is reported distinctly
        assertThat(response.result()).isEqualTo(DropResult.EMPTY_HAND);
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
            "request-create", "testbot", uuid, "world", 10.0, 64.0, 20.0, null, "test.perm",
            JoinMode.LEGACY_SPAWN, null
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

    @Test
    void handleCreatePlayer_shouldSurfaceFullLoginDenialAsTypedError()
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final IBotLoginDriver loginDriver = mock();
        when(fixture.nmsAdapter().loginDriver()).thenReturn(loginDriver);
        when(loginDriver.login(any())).thenReturn(new IBotLoginOutcome.Denied(BotJoinPhase.LOGIN, "Banned"));
        final CreatePlayer.Command command = new CreatePlayer.Command(
            "request-full", "fullbot", null, "world", null, null, null, null, null, JoinMode.FULL_LOGIN, "en_us");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            stubFullLoginBukkitStatics(bukkit);

            // execute
            final Throwable thrown = catchThrowable(() -> fixture.playerActions().handleCreatePlayer(command));

            // verify
            assertThat(thrown)
                .isInstanceOf(AgentProtocolException.class)
                .satisfies(failure -> assertThat(((AgentProtocolException) failure).errorCode())
                    .isEqualTo(AgentErrorCode.PLAYER_JOIN_DENIED))
                .hasMessageContaining("Banned");
        }
    }

    @Test
    void handleCreatePlayer_shouldSurfaceFullLoginDriverTimeoutAsTypedError()
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final IBotLoginDriver loginDriver = mock();
        when(fixture.nmsAdapter().loginDriver()).thenReturn(loginDriver);
        when(loginDriver.login(any())).thenReturn(new IBotLoginOutcome.TimedOut(BotJoinPhase.LOGIN));
        final CreatePlayer.Command command = new CreatePlayer.Command(
            "request-full-to", "fullbot", null, "world", null, null, null, null, null, JoinMode.FULL_LOGIN, null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            stubFullLoginBukkitStatics(bukkit);

            // execute
            final Throwable thrown = catchThrowable(() -> fixture.playerActions().handleCreatePlayer(command));

            // verify
            assertThat(thrown)
                .isInstanceOf(AgentProtocolException.class)
                .satisfies(failure -> assertThat(((AgentProtocolException) failure).errorCode())
                    .isEqualTo(AgentErrorCode.PLAYER_JOIN_TIMEOUT))
                .hasMessageContaining("did not complete the login pipeline")
                .hasMessageContaining("LOGIN");
        }
    }

    @Test
    void handleCreatePlayer_shouldSurfaceMissingJoinEventAsTypedTimeout()
    {
        // setup — a short 1-second budget so the join-latch residual wait expires quickly. The driver reports
        // Joined, but no PlayerJoinEvent ever fires (the mocked plugin manager registers nothing).
        final PlayerActionsFixture fixture = createPlayerActionsFixture(1L);
        final IBotLoginDriver loginDriver = mock();
        when(fixture.nmsAdapter().loginDriver()).thenReturn(loginDriver);
        when(loginDriver.login(any())).thenReturn(new IBotLoginOutcome.Joined("fullbot"));
        final CreatePlayer.Command command = new CreatePlayer.Command(
            "request-full-nj", "fullbot", null, "world", null, null, null, null, null, JoinMode.FULL_LOGIN, null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            stubFullLoginBukkitStatics(bukkit);

            // execute
            final Throwable thrown = catchThrowable(() -> fixture.playerActions().handleCreatePlayer(command));

            // verify
            assertThat(thrown)
                .isInstanceOf(AgentProtocolException.class)
                .satisfies(failure -> assertThat(((AgentProtocolException) failure).errorCode())
                    .isEqualTo(AgentErrorCode.PLAYER_JOIN_TIMEOUT))
                .hasMessageContaining("no PlayerJoinEvent fired");
        }
    }

    /**
     * Stubs the Bukkit statics a FULL_LOGIN join touches: NOT the primary thread (the handler rejects
     * main-thread execution), a server exposing a port, a known world named {@code "world"}, a no-op plugin
     * manager, and a scheduler that runs main-thread callables inline.
     */
    private static void stubFullLoginBukkitStatics(MockedStatic<Bukkit> bukkit)
    {
        final org.bukkit.Server server = mock();
        when(server.getPort()).thenReturn(25_565);
        final World world = mock();
        final PluginManager pluginManager = mock();
        final BukkitScheduler scheduler = mock();
        when(scheduler.callSyncMethod(any(), any())).thenAnswer(invocation ->
        {
            final Callable<?> callable = invocation.getArgument(1);
            return CompletableFuture.completedFuture(callable.call());
        });

        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
    }

    @Test
    void handlePlayerChat_shouldInvokePlayerChatOnMainThread()
        throws Exception
    {
        // setup
        final PlayerActionsFixture fixture = createPlayerActionsFixture();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        fixture.playerStore().registerSyntheticPlayer(uuid, player);
        final PlayerChat.Command command = new PlayerChat.Command("request-chat", uuid, "hello world");

        // execute
        final PlayerChat.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handlePlayerChat(command);
        }

        // verify
        assertThat(response).isNotNull();
        verify(player).chat("hello world");
    }

    @Test
    void handlePlayerChat_shouldPropagateWhenPlayerUnknown()
    {
        // setup
        final AgentPlayerActions playerActions = createPlayerActions();
        final PlayerChat.Command command =
            new PlayerChat.Command("request-chat-unknown", UUID.randomUUID(), "hello");

        // execute + verify
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertThatThrownBy(() -> playerActions.handlePlayerChat(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not registered");
        }
    }

    private static AgentPlayerActions createPlayerActions()
    {
        return createPlayerActionsFixture().playerActions();
    }

    private static PlayerActionsFixture createPlayerActionsFixture()
    {
        return createPlayerActionsFixture(null);
    }

    private static PlayerActionsFixture createPlayerActionsFixture(
        @org.jspecify.annotations.Nullable Long syncOperationTimeoutSeconds)
    {
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        final AgentMainThreadExecutor mainThreadExecutor = syncOperationTimeoutSeconds == null
            ? new AgentMainThreadExecutor(plugin)
            : new AgentMainThreadExecutor(plugin, syncOperationTimeoutSeconds);
        final AgentSyntheticPlayerStore playerStore = new AgentSyntheticPlayerStore();
        final ObjectMapper objectMapper = new ObjectMapper();
        final IBotPlayerNmsAdapter nmsAdapter = mock();

        final AgentPlayerActions playerActions = new AgentPlayerActions(
            plugin,
            mainThreadExecutor,
            playerStore,
            nmsAdapter
        );
        final AgentPlayerStateActions stateActions = new AgentPlayerStateActions(
            mainThreadExecutor,
            playerStore,
            objectMapper,
            nmsAdapter
        );
        return new PlayerActionsFixture(playerActions, stateActions, playerStore, nmsAdapter);
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
        AgentPlayerStateActions stateActions,
        AgentSyntheticPlayerStore playerStore,
        IBotPlayerNmsAdapter nmsAdapter)
    {
    }
}
