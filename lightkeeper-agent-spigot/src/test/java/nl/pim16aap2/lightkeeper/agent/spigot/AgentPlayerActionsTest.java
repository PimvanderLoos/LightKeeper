package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
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

import static org.mockito.ArgumentMatchers.anyString;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentPlayerActionsTest
{
    @Test
    void handleCreatePlayer_shouldReturnErrorWhenNameIsBlank()
        throws Exception
    {
        // setup
        final AgentPlayerActions playerActions = createPlayerActions();
        final Map<String, String> arguments = Map.of(
            "name", "   ",
            "worldName", "world"
        );

        // execute
        final AgentResponse response = playerActions.handleCreatePlayer("request-1", arguments);

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("name");
    }

    @Test
    void handleCreatePlayer_shouldReturnErrorWhenWorldNameIsBlank()
        throws Exception
    {
        // setup
        final AgentPlayerActions playerActions = createPlayerActions();
        final Map<String, String> arguments = Map.of(
            "name", "bot",
            "worldName", "   "
        );

        // execute
        final AgentResponse response = playerActions.handleCreatePlayer("request-2", arguments);

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("worldName");
    }

    @Test
    void handleExecutePlayerCommand_shouldReturnErrorWhenCommandIsBlank()
        throws Exception
    {
        // setup
        final AgentPlayerActions playerActions = createPlayerActions();
        final Map<String, String> arguments = Map.of(
            "uuid", UUID.randomUUID().toString(),
            "command", "   "
        );

        // execute
        final AgentResponse response = playerActions.handleExecutePlayerCommand("request-3", arguments);

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("command");
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
        final Map<String, String> arguments = Map.of(
            "uuid", uuid.toString(),
            "x", "1",
            "y", "64",
            "z", "2",
            "blockFace", "north"
        );

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            response = fixture.playerActions().handleLeftClickBlock("request-click", arguments);
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
        final Map<String, String> arguments = Map.of(
            "uuid", UUID.randomUUID().toString(),
            "x", "1",
            "y", "64",
            "z", "2",
            "blockFace", "not-a-face"
        );

        // execute
        final AgentResponse response = playerActions.handleRightClickBlock("request-click", arguments);

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

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleGetPlayerMessages("request-messages", Map.of(
                "uuid", uuid.toString()
            ));
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

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleGetPlayerChatComponents("request-components", Map.of(
                "uuid", uuid.toString()
            ));
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
        when(inventory.getContents()).thenReturn(new ItemStack[]{
            null,
            new ItemStack(Material.AIR),
            stone
        });
        fixture.playerStore().registerSyntheticPlayer(uuid, player);

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleGetPlayerInventory("request-inventory", Map.of(
                "uuid", uuid.toString()
            ));
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
        final Map<String, String> arguments = Map.of(
            "uuid", UUID.randomUUID().toString(),
            "worldName", "   ",
            "x", "0",
            "y", "64",
            "z", "0"
        );

        // execute
        final AgentResponse response = playerActions.handleTeleportPlayer("request-tp-blank", arguments);

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
        final Map<String, String> arguments = Map.of(
            "uuid", UUID.randomUUID().toString(),
            "material", "not_a_real_material",
            "x", "1",
            "y", "64",
            "z", "2"
        );

        // execute
        final AgentResponse response;
        try (MockedStatic<Material> materialMockedStatic = mockStatic(Material.class))
        {
            materialMockedStatic.when(() -> Material.matchMaterial(anyString(), eq(true))).thenReturn(null);
            response = playerActions.handlePlacePlayerBlock("request-place", arguments);
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
        when(player.getInventory().getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
        fixture.playerStore().registerSyntheticPlayer(uuid, player);

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            response = fixture.playerActions().handleDropItem("request-drop", Map.of("uuid", uuid.toString()));
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
        final Map<String, String> arguments = Map.of(
            "name", "testbot",
            "uuid", uuid.toString(),
            "worldName", "world",
            "x", "10.0",
            "y", "64.0",
            "z", "20.0",
            "permissions", "test.perm"
        );

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = fixture.playerActions().handleCreatePlayer("request-create", arguments);
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
        AgentSyntheticPlayerStore playerStore,
        IBotPlayerNmsAdapter nmsAdapter)
    {
    }
}
