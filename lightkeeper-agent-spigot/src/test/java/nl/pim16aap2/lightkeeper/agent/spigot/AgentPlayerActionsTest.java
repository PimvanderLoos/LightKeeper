package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.CreatePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.DropItemCommand;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommandCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerInventoryCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerChatComponentsCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerMessagesCommand;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.PlacePlayerBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.RightClickBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.TeleportPlayerCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
        final ExecutePlayerCommandCommand command =
            new ExecutePlayerCommandCommand("request-cmd", uuid, "/gamemode creative");

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleExecutePlayerCommand(command);
        }

        // verify
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
        final LeftClickBlockCommand command =
            new LeftClickBlockCommand("request-click", uuid, 1, 64, 2, "NORTH");

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            response = fixture.playerActions().handleLeftClickBlock(command);
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("cancelled", "false");
        verify(pluginManager).callEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAction()).isEqualTo(Action.LEFT_CLICK_BLOCK);
        assertThat(eventCaptor.getValue().getBlockFace()).isEqualTo(BlockFace.NORTH);
        assertThat(eventCaptor.getValue().getPlayer()).isSameAs(player);
    }

    @Test
    void handleRightClickBlock_shouldReturnErrorWhenBlockFaceIsUnknown()
        throws Exception
    {
        // setup
        final AgentPlayerActions playerActions = createPlayerActions();
        final RightClickBlockCommand command =
            new RightClickBlockCommand("request-click", UUID.randomUUID(), 1, 64, 2, "not-a-face");

        // execute
        final AgentResponse response = playerActions.handleRightClickBlock(command);

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("block face");
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
        final GetPlayerMessagesCommand command = new GetPlayerMessagesCommand("request-messages", uuid);

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleGetPlayerMessages(command);
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("messagesJson", "[\"first\",\"second\"]");
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
        final GetPlayerChatComponentsCommand command =
            new GetPlayerChatComponentsCommand("request-components", uuid);

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleGetPlayerChatComponents(command);
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("componentsJson", "[\"{\\\"text\\\":\\\"hello\\\"}\"]");
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
        final GetPlayerInventoryCommand command = new GetPlayerInventoryCommand("request-inventory", uuid);

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleGetPlayerInventory(command);
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data().get("inventoryJson"))
            .contains("\"slot\":2")
            .contains("\"materialKey\":\"minecraft:stone\"")
            .contains("\"lore\":[]");
    }

    @Test
    void handleTeleportPlayer_shouldReturnErrorWhenWorldNameIsBlank()
        throws Exception
    {
        // setup
        final AgentPlayerActions playerActions = createPlayerActions();
        final TeleportPlayerCommand command =
            new TeleportPlayerCommand("request-tp-blank", UUID.randomUUID(), "   ", 0, 64, 0);

        // execute
        final AgentResponse response = playerActions.handleTeleportPlayer(command);

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("worldName");
    }

    @Test
    void handlePlacePlayerBlock_shouldReturnErrorWhenMaterialIsUnknown()
        throws Exception
    {
        // setup
        final AgentPlayerActions playerActions = createPlayerActions();
        final PlacePlayerBlockCommand command =
            new PlacePlayerBlockCommand("request-place", UUID.randomUUID(), "not_a_real_material", 1, 64, 2);

        // execute
        final AgentResponse response;
        try (MockedStatic<Material> materialMockedStatic = mockStatic(Material.class))
        {
            materialMockedStatic.when(() -> Material.matchMaterial(anyString(), eq(true))).thenReturn(null);
            response = playerActions.handlePlacePlayerBlock(command);
        }

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("Unknown material");
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
        final DropItemCommand command = new DropItemCommand("request-drop", uuid);

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleDropItem(command);
        }

        // verify - no item to drop, so dropped=false
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("dropped", "false");
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
        when(player.addAttachment(any())).thenReturn(mock());
        final CreatePlayerCommand command = new CreatePlayerCommand(
            "request-create", "testbot", uuid, "world", 10.0, 64.0, 20.0, null, "test.perm"
        );

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = fixture.playerActions().handleCreatePlayer(command);
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("uuid", uuid.toString());
        assertThat(response.data()).containsEntry("name", "testbot");
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
