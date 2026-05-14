package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.BlockType;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.ClickMenuSlot;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.CreatePlayer;
import nl.pim16aap2.lightkeeper.protocol.DragMenuSlots;
import nl.pim16aap2.lightkeeper.protocol.DropItem;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommand;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.GetOpenMenu;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerChatComponents;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerInventory;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerMessages;
import nl.pim16aap2.lightkeeper.protocol.GetServerPlatform;
import nl.pim16aap2.lightkeeper.protocol.GetServerTick;
import nl.pim16aap2.lightkeeper.protocol.Handshake;
import nl.pim16aap2.lightkeeper.protocol.IAgentCommand;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoaded;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlock;
import nl.pim16aap2.lightkeeper.protocol.LoadChunk;
import nl.pim16aap2.lightkeeper.protocol.MainWorld;
import nl.pim16aap2.lightkeeper.protocol.NewWorld;
import nl.pim16aap2.lightkeeper.protocol.PlacePlayerBlock;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListener;
import nl.pim16aap2.lightkeeper.protocol.RemovePlayer;
import nl.pim16aap2.lightkeeper.protocol.RightClickBlock;
import nl.pim16aap2.lightkeeper.protocol.SetBlock;
import nl.pim16aap2.lightkeeper.protocol.TeleportPlayer;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunk;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListener;
import nl.pim16aap2.lightkeeper.protocol.WaitTicks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentRequestDispatcherTest
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void handleRequestLine_shouldCompleteHandshakeWhenTokenAndProtocolMatch()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        final String requestLine = toJson(new Handshake.Command("request-0", "token", 1, ""));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::getBukkitVersion).thenReturn("1.21.11");
            result = fixture.dispatcher().handleRequestLine(requestLine, false);
        }

        // verify
        assertThat(result.handshakeCompleted()).isTrue();
        assertThat(isSuccess(result.responseJson())).isTrue();
    }

    @Test
    void handleRequestLine_shouldDispatchWorldActionsWhenHandshakeCompleted()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.worldActions().handleMainWorld(any(MainWorld.Command.class)))
            .thenReturn(new MainWorld.Response("request-world", "world"));
        final String requestLine = toJson(new MainWorld.Command("request-world"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(result.handshakeCompleted()).isTrue();
        assertThat(isSuccess(result.responseJson())).isTrue();
        verify(fixture.worldActions()).handleMainWorld(any(MainWorld.Command.class));
    }

    @Test
    void handleRequestLine_shouldReturnRequestFailedWhenActionThrowsException()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.worldActions().handleMainWorld(any(MainWorld.Command.class)))
            .thenThrow(new IllegalStateException("boom"));
        final String requestLine = toJson(new MainWorld.Command("request-fail"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("REQUEST_FAILED");
        assertThat(result.handshakeCompleted()).isTrue();
    }

    @Test
    void handleRequestLine_shouldDispatchSupportedActionsToTheirHandlers()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        final UUID uuid = UUID.randomUUID();

        when(fixture.worldActions().handleNewWorld(any(NewWorld.Command.class)))
            .thenReturn(new NewWorld.Response("request-1", "w"));
        when(fixture.worldActions().handleExecuteCommand(any(ExecuteCommand.Command.class)))
            .thenReturn(new ExecuteCommand.Response("request-2", true));
        when(fixture.worldActions().handleBlockType(any(BlockType.Command.class)))
            .thenReturn(new BlockType.Response("request-3", "STONE"));
        when(fixture.worldActions().handleSetBlock(any(SetBlock.Command.class)))
            .thenReturn(new SetBlock.Response("request-4", "STONE"));
        when(fixture.playerActions().handleCreatePlayer(any(CreatePlayer.Command.class)))
            .thenReturn(new CreatePlayer.Response("request-5", uuid, "bot"));
        when(fixture.playerActions().handleRemovePlayer(any(RemovePlayer.Command.class)))
            .thenReturn(new RemovePlayer.Response("request-6"));
        when(fixture.playerActions().handleExecutePlayerCommand(any(ExecutePlayerCommand.Command.class)))
            .thenReturn(new ExecutePlayerCommand.Response("request-7", true));
        when(fixture.playerActions().handlePlacePlayerBlock(any(PlacePlayerBlock.Command.class)))
            .thenReturn(new PlacePlayerBlock.Response("request-8", "minecraft:stone"));
        when(fixture.playerActions().handleLeftClickBlock(any(LeftClickBlock.Command.class)))
            .thenReturn(new LeftClickBlock.Response("request-9", false));
        when(fixture.playerActions().handleRightClickBlock(any(RightClickBlock.Command.class)))
            .thenReturn(new RightClickBlock.Response("request-10", false));
        when(fixture.menuActions().handleGetOpenMenu(any(GetOpenMenu.Command.class)))
            .thenReturn(new GetOpenMenu.Response("request-11", false, null, null));
        when(fixture.menuActions().handleClickMenuSlot(any(ClickMenuSlot.Command.class)))
            .thenReturn(new ClickMenuSlot.Response("request-12"));
        when(fixture.menuActions().handleDragMenuSlots(any(DragMenuSlots.Command.class)))
            .thenReturn(new DragMenuSlots.Response("request-13"));
        when(fixture.playerActions().handleGetPlayerMessages(any(GetPlayerMessages.Command.class)))
            .thenReturn(new GetPlayerMessages.Response("request-14", java.util.List.of()));
        when(fixture.worldActions().handleWaitTicks(any(WaitTicks.Command.class)))
            .thenReturn(new WaitTicks.Response("request-15", 0L, 0L));
        when(fixture.worldActions().handleGetServerTick(any(GetServerTick.Command.class)))
            .thenReturn(new GetServerTick.Response("request-16", 0L));
        when(fixture.playerActions().handleTeleportPlayer(any(TeleportPlayer.Command.class)))
            .thenReturn(new TeleportPlayer.Response("request-17", true));
        when(fixture.worldActions().handleLoadChunk(any(LoadChunk.Command.class)))
            .thenReturn(new LoadChunk.Response("request-18", true));
        when(fixture.worldActions().handleUnloadChunk(any(UnloadChunk.Command.class)))
            .thenReturn(new UnloadChunk.Response("request-19", true));
        when(fixture.worldActions().handleIsChunkLoaded(any(IsChunkLoaded.Command.class)))
            .thenReturn(new IsChunkLoaded.Response("request-20", true));
        when(fixture.playerActions().handleGetPlayerInventory(any(GetPlayerInventory.Command.class)))
            .thenReturn(new GetPlayerInventory.Response("request-21", "[]"));
        when(fixture.playerActions().handleDropItem(any(DropItem.Command.class)))
            .thenReturn(new DropItem.Response("request-22", false));
        when(fixture.playerActions().handleRegisterEventListener(any(RegisterEventListener.Command.class)))
            .thenReturn(new RegisterEventListener.Response("request-23"));
        when(fixture.playerActions().handleGetCapturedEvents(any(GetCapturedEvents.Command.class)))
            .thenReturn(new GetCapturedEvents.Response("request-24", "[]"));
        when(fixture.playerActions().handleClearCapturedEvents(any(ClearCapturedEvents.Command.class)))
            .thenReturn(new ClearCapturedEvents.Response("request-25"));
        when(fixture.playerActions().handleUnregisterEventListener(any(UnregisterEventListener.Command.class)))
            .thenReturn(new UnregisterEventListener.Response("request-26"));
        when(fixture.playerActions().handleGetPlayerChatComponents(any(GetPlayerChatComponents.Command.class)))
            .thenReturn(new GetPlayerChatComponents.Response("request-27"));
        when(fixture.worldActions().handleGetServerPlatform(any(GetServerPlatform.Command.class)))
            .thenReturn(new GetServerPlatform.Response("request-28", "CraftBukkit", "1.21"));

        // execute
        fixture.dispatcher().handleRequestLine(toJson(new NewWorld.Command("request-1", "w", "NORMAL", "NORMAL", 0L)), true);
        fixture.dispatcher().handleRequestLine(toJson(new ExecuteCommand.Command("request-2", CommandSource.CONSOLE, "time set day")), true);
        fixture.dispatcher().handleRequestLine(toJson(new BlockType.Command("request-3", "world", 0, 64, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new SetBlock.Command("request-4", "world", 0, 64, 0, "stone")), true);
        fixture.dispatcher().handleRequestLine(toJson(new CreatePlayer.Command("request-5", "bot", uuid, "world", null, null, null, null, null)), true);
        fixture.dispatcher().handleRequestLine(toJson(new RemovePlayer.Command("request-6", uuid)), true);
        fixture.dispatcher().handleRequestLine(toJson(new ExecutePlayerCommand.Command("request-7", uuid, "gamemode creative")), true);
        fixture.dispatcher().handleRequestLine(toJson(new PlacePlayerBlock.Command("request-8", uuid, "stone", 0, 64, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new LeftClickBlock.Command("request-9", uuid, 0, 64, 0, "UP")), true);
        fixture.dispatcher().handleRequestLine(toJson(new RightClickBlock.Command("request-10", uuid, 0, 64, 0, "UP")), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetOpenMenu.Command("request-11", uuid)), true);
        fixture.dispatcher().handleRequestLine(toJson(new ClickMenuSlot.Command("request-12", uuid, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new DragMenuSlots.Command("request-13", uuid, "stone", new int[]{0, 1})), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetPlayerMessages.Command("request-14", uuid)), true);
        fixture.dispatcher().handleRequestLine(toJson(new WaitTicks.Command("request-15", 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetServerTick.Command("request-16")), true);
        fixture.dispatcher().handleRequestLine(toJson(new TeleportPlayer.Command("request-17", uuid, "world", 0, 64, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new LoadChunk.Command("request-18", "world", 0, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new UnloadChunk.Command("request-19", "world", 0, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new IsChunkLoaded.Command("request-20", "world", 0, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetPlayerInventory.Command("request-21", uuid)), true);
        fixture.dispatcher().handleRequestLine(toJson(new DropItem.Command("request-22", uuid)), true);
        fixture.dispatcher().handleRequestLine(toJson(new RegisterEventListener.Command("request-23", "org.bukkit.event.player.PlayerJoinEvent")), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetCapturedEvents.Command("request-24", "org.bukkit.event.player.PlayerJoinEvent")), true);
        fixture.dispatcher().handleRequestLine(toJson(new ClearCapturedEvents.Command("request-25", "org.bukkit.event.player.PlayerJoinEvent")), true);
        fixture.dispatcher().handleRequestLine(toJson(new UnregisterEventListener.Command("request-26", "org.bukkit.event.player.PlayerJoinEvent")), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetPlayerChatComponents.Command("request-27", uuid)), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetServerPlatform.Command("request-28")), true);

        // verify
        verify(fixture.worldActions()).handleNewWorld(any(NewWorld.Command.class));
        verify(fixture.worldActions()).handleExecuteCommand(any(ExecuteCommand.Command.class));
        verify(fixture.worldActions()).handleBlockType(any(BlockType.Command.class));
        verify(fixture.worldActions()).handleSetBlock(any(SetBlock.Command.class));
        verify(fixture.playerActions()).handleCreatePlayer(any(CreatePlayer.Command.class));
        verify(fixture.playerActions()).handleRemovePlayer(any(RemovePlayer.Command.class));
        verify(fixture.playerActions()).handleExecutePlayerCommand(any(ExecutePlayerCommand.Command.class));
        verify(fixture.playerActions()).handlePlacePlayerBlock(any(PlacePlayerBlock.Command.class));
        verify(fixture.playerActions()).handleLeftClickBlock(any(LeftClickBlock.Command.class));
        verify(fixture.playerActions()).handleRightClickBlock(any(RightClickBlock.Command.class));
        verify(fixture.menuActions()).handleGetOpenMenu(any(GetOpenMenu.Command.class));
        verify(fixture.menuActions()).handleClickMenuSlot(any(ClickMenuSlot.Command.class));
        verify(fixture.menuActions()).handleDragMenuSlots(any(DragMenuSlots.Command.class));
        verify(fixture.playerActions()).handleGetPlayerMessages(any(GetPlayerMessages.Command.class));
        verify(fixture.worldActions()).handleWaitTicks(any(WaitTicks.Command.class));
        verify(fixture.worldActions()).handleGetServerTick(any(GetServerTick.Command.class));
        verify(fixture.playerActions()).handleTeleportPlayer(any(TeleportPlayer.Command.class));
        verify(fixture.worldActions()).handleLoadChunk(any(LoadChunk.Command.class));
        verify(fixture.worldActions()).handleUnloadChunk(any(UnloadChunk.Command.class));
        verify(fixture.worldActions()).handleIsChunkLoaded(any(IsChunkLoaded.Command.class));
        verify(fixture.playerActions()).handleGetPlayerInventory(any(GetPlayerInventory.Command.class));
        verify(fixture.playerActions()).handleDropItem(any(DropItem.Command.class));
        verify(fixture.playerActions()).handleRegisterEventListener(any(RegisterEventListener.Command.class));
        verify(fixture.playerActions()).handleGetCapturedEvents(any(GetCapturedEvents.Command.class));
        verify(fixture.playerActions()).handleClearCapturedEvents(any(ClearCapturedEvents.Command.class));
        verify(fixture.playerActions()).handleUnregisterEventListener(any(UnregisterEventListener.Command.class));
        verify(fixture.playerActions()).handleGetPlayerChatComponents(any(GetPlayerChatComponents.Command.class));
        verify(fixture.worldActions()).handleGetServerPlatform(any(GetServerPlatform.Command.class));
    }

    @Test
    void cleanupSyntheticPlayers_shouldDelegateToPlayerActions()
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();

        // execute
        fixture.dispatcher().cleanupSyntheticPlayers();

        // verify
        verify(fixture.playerActions()).cleanupSyntheticPlayers();
    }

    @Test
    void handleRequestLine_shouldReturnInvalidRequestWhenJsonIsMalformed()
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("token", 1, "");
        final String malformedJson = "{ this is not valid json }";

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(malformedJson, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(requestId(result.responseJson())).isEqualTo("unknown");
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void handleRequestLine_shouldRejectNonHandshakeRequestBeforeHandshakeCompletes()
        throws Exception
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("token", 1, "");
        final String requestLine = toJson(new MainWorld.Command("request-1"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(requestLine, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(requestId(result.responseJson())).isEqualTo("request-1");
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("HANDSHAKE_REQUIRED");
    }

    @Test
    void handleRequestLine_shouldReturnAuthFailedWhenHandshakeTokenDoesNotMatch()
        throws Exception
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("expected-token", 1, "");
        final String requestLine = toJson(new Handshake.Command("request-2", "wrong-token", 1, ""));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(requestLine, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(requestId(result.responseJson())).isEqualTo("request-2");
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("AUTH_FAILED");
    }

    @Test
    void handleRequestLine_shouldReturnProtocolMismatchWhenProtocolVersionDiffers()
        throws Exception
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("expected-token", 1, "");
        final String requestLine = toJson(new Handshake.Command("request-3", "expected-token", 0, ""));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(requestLine, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(requestId(result.responseJson())).isEqualTo("request-3");
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("PROTOCOL_MISMATCH");
        assertThat(errorMessage(result.responseJson())).contains("expected=1").contains("actual=0");
    }

    @Test
    void handleRequestLine_shouldReturnAgentShaMismatchWhenExpectedShaDoesNotMatch()
        throws Exception
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("expected-token", 1, "expected-sha");
        final String requestLine = toJson(new Handshake.Command("request-4", "expected-token", 1, "actual-sha"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(requestLine, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(requestId(result.responseJson())).isEqualTo("request-4");
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("AGENT_SHA_MISMATCH");
    }

    private static AgentRequestDispatcher createDispatcher(String authToken, int protocolVersion, String expectedSha)
    {
        final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        final AgentSyntheticPlayerStore playerStore = new AgentSyntheticPlayerStore();
        final AgentMenuActions menuActions = new AgentMenuActions(
            mainThreadExecutor,
            playerStore,
            objectMapper
        );
        final AgentWorldActions worldActions = new AgentWorldActions(
            plugin,
            mainThreadExecutor,
            new AtomicLong(0L)
        );
        final IBotPlayerNmsAdapter nmsAdapter = mock();
        final AgentPlayerActions playerActions = new AgentPlayerActions(
            plugin,
            mainThreadExecutor,
            playerStore,
            objectMapper,
            nmsAdapter
        );

        return new AgentRequestDispatcher(
            objectMapper,
            worldActions,
            playerActions,
            menuActions,
            Logger.getLogger(AgentRequestDispatcherTest.class.getName()),
            authToken,
            protocolVersion,
            expectedSha
        );
    }

    private static DispatcherFixture createDispatcherFixture()
    {
        final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final AgentWorldActions worldActions = mock();
        final AgentPlayerActions playerActions = mock();
        final AgentMenuActions menuActions = mock();
        final AgentRequestDispatcher dispatcher = new AgentRequestDispatcher(
            objectMapper,
            worldActions,
            playerActions,
            menuActions,
            Logger.getLogger(AgentRequestDispatcherTest.class.getName()),
            "token",
            1,
            ""
        );
        return new DispatcherFixture(dispatcher, worldActions, playerActions, menuActions);
    }

    private record DispatcherFixture(
        AgentRequestDispatcher dispatcher,
        AgentWorldActions worldActions,
        AgentPlayerActions playerActions,
        AgentMenuActions menuActions)
    {
    }

    @SuppressWarnings("rawtypes")
    private static String toJson(IAgentCommand command)
        throws Exception
    {
        return OBJECT_MAPPER.writeValueAsString(command);
    }

    private static boolean isSuccess(String responseJson)
    {
        try
        {
            return OBJECT_MAPPER.readTree(responseJson).path("success").asBoolean();
        }
        catch (Exception exception)
        {
            throw new IllegalArgumentException("Failed to parse response JSON: " + responseJson, exception);
        }
    }

    private static String errorCode(String responseJson)
    {
        try
        {
            return OBJECT_MAPPER.readTree(responseJson).path("errorCode").asText("");
        }
        catch (Exception exception)
        {
            throw new IllegalArgumentException("Failed to parse response JSON: " + responseJson, exception);
        }
    }

    private static String errorMessage(String responseJson)
    {
        try
        {
            return OBJECT_MAPPER.readTree(responseJson).path("errorMessage").asText("");
        }
        catch (Exception exception)
        {
            throw new IllegalArgumentException("Failed to parse response JSON: " + responseJson, exception);
        }
    }

    private static String requestId(String responseJson)
    {
        try
        {
            final JsonNode node = OBJECT_MAPPER.readTree(responseJson);
            return node.path("requestId").asText("unknown");
        }
        catch (Exception exception)
        {
            throw new IllegalArgumentException("Failed to parse response JSON: " + responseJson, exception);
        }
    }
}
