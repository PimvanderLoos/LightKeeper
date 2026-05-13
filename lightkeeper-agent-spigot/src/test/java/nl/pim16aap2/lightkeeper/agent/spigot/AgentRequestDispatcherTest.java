package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.IAgentCommand;
import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.BlockTypeCommand;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEventsCommand;
import nl.pim16aap2.lightkeeper.protocol.ClickMenuSlotCommand;
import nl.pim16aap2.lightkeeper.protocol.CreatePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.DragMenuSlotsCommand;
import nl.pim16aap2.lightkeeper.protocol.DropItemCommand;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommandCommand;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommandCommand;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEventsCommand;
import nl.pim16aap2.lightkeeper.protocol.GetOpenMenuCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerInventoryCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerMessagesCommand;
import nl.pim16aap2.lightkeeper.protocol.GetServerTickCommand;
import nl.pim16aap2.lightkeeper.protocol.HandshakeCommand;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoadedCommand;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.LoadChunkCommand;
import nl.pim16aap2.lightkeeper.protocol.MainWorldCommand;
import nl.pim16aap2.lightkeeper.protocol.NewWorldCommand;
import nl.pim16aap2.lightkeeper.protocol.PlacePlayerBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListenerCommand;
import nl.pim16aap2.lightkeeper.protocol.RemovePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.RightClickBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.SetBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.TeleportPlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunkCommand;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListenerCommand;
import nl.pim16aap2.lightkeeper.protocol.WaitTicksCommand;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
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
        final String requestLine = toJson(new HandshakeCommand("request-0", "token", 1, ""));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::getBukkitVersion).thenReturn("1.21.11");
            result = fixture.dispatcher().handleRequestLine(requestLine, false);
        }

        // verify
        assertThat(result.handshakeCompleted()).isTrue();
        assertThat(result.response().success()).isTrue();
    }

    @Test
    void handleRequestLine_shouldDispatchWorldActionsWhenHandshakeCompleted()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.worldActions().handleMainWorld(any(MainWorldCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-world", Map.of("world", "world")));
        final String requestLine = toJson(new MainWorldCommand("request-world"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(result.handshakeCompleted()).isTrue();
        assertThat(result.response().success()).isTrue();
        verify(fixture.worldActions()).handleMainWorld(any(MainWorldCommand.class));
    }

    @Test
    void handleRequestLine_shouldReturnRequestFailedWhenActionThrowsException()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.worldActions().handleMainWorld(any(MainWorldCommand.class)))
            .thenThrow(new IllegalStateException("boom"));
        final String requestLine = toJson(new MainWorldCommand("request-fail"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(result.response().success()).isFalse();
        assertThat(result.response().errorCode()).isEqualTo("REQUEST_FAILED");
        assertThat(result.handshakeCompleted()).isTrue();
    }

    @Test
    void handleRequestLine_shouldDispatchSupportedActionsToTheirHandlers()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        final UUID uuid = UUID.randomUUID();
        final String eventClass = "org.bukkit.event.Event";

        when(fixture.worldActions().handleNewWorld(any(NewWorldCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-1", Map.of()));
        when(fixture.worldActions().handleExecuteCommand(any(ExecuteCommandCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-2", Map.of()));
        when(fixture.worldActions().handleBlockType(any(BlockTypeCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-3", Map.of()));
        when(fixture.worldActions().handleSetBlock(any(SetBlockCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-4", Map.of()));
        when(fixture.playerActions().handleCreatePlayer(any(CreatePlayerCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-5", Map.of()));
        when(fixture.playerActions().handleRemovePlayer(any(RemovePlayerCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-6", Map.of()));
        when(fixture.playerActions().handleExecutePlayerCommand(any(ExecutePlayerCommandCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-7", Map.of()));
        when(fixture.playerActions().handlePlacePlayerBlock(any(PlacePlayerBlockCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-8", Map.of()));
        when(fixture.playerActions().handleLeftClickBlock(any(LeftClickBlockCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-9", Map.of()));
        when(fixture.playerActions().handleRightClickBlock(any(RightClickBlockCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-10", Map.of()));
        when(fixture.menuActions().handleGetOpenMenu(any(GetOpenMenuCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-11", Map.of()));
        when(fixture.menuActions().handleClickMenuSlot(any(ClickMenuSlotCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-12", Map.of()));
        when(fixture.menuActions().handleDragMenuSlots(any(DragMenuSlotsCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-13", Map.of()));
        when(fixture.playerActions().handleGetPlayerMessages(any(GetPlayerMessagesCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-14", Map.of()));
        when(fixture.worldActions().handleWaitTicks(any(WaitTicksCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-15", Map.of()));
        when(fixture.worldActions().handleGetServerTick(any(GetServerTickCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-16", Map.of()));
        when(fixture.playerActions().handleTeleportPlayer(any(TeleportPlayerCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-17", Map.of()));
        when(fixture.worldActions().handleLoadChunk(any(LoadChunkCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-18", Map.of()));
        when(fixture.worldActions().handleUnloadChunk(any(UnloadChunkCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-19", Map.of("unloaded", "true")));
        when(fixture.worldActions().handleIsChunkLoaded(any(IsChunkLoadedCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-20", Map.of("loaded", "true")));
        when(fixture.playerActions().handleGetPlayerInventory(any(GetPlayerInventoryCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-21", Map.of("inventoryJson", "[]")));
        when(fixture.playerActions().handleDropItem(any(DropItemCommand.class)))
            .thenReturn(AgentResponses.successResponse("request-22", Map.of("dropped", "false")));
        when(fixture.eventCapture().getCapturedEvents(eq(eventClass)))
            .thenReturn(List.of(Map.of("getEventName", "Event")));

        // execute
        fixture.dispatcher().handleRequestLine(toJson(new NewWorldCommand("request-1", "w", "NORMAL", "NORMAL", 0L)), true);
        fixture.dispatcher().handleRequestLine(
            toJson(new ExecuteCommandCommand("request-2", "CONSOLE", "time set day")), true);
        fixture.dispatcher().handleRequestLine(toJson(new BlockTypeCommand("request-3", "world", 0, 64, 0)), true);
        fixture.dispatcher().handleRequestLine(
            toJson(new SetBlockCommand("request-4", "world", 0, 64, 0, "stone")), true);
        fixture.dispatcher().handleRequestLine(
            toJson(new CreatePlayerCommand("request-5", "bot", uuid, "world", null, null, null, null, null)), true);
        fixture.dispatcher().handleRequestLine(toJson(new RemovePlayerCommand("request-6", uuid)), true);
        fixture.dispatcher().handleRequestLine(
            toJson(new ExecutePlayerCommandCommand("request-7", uuid, "gamemode creative")), true);
        fixture.dispatcher().handleRequestLine(
            toJson(new PlacePlayerBlockCommand("request-8", uuid, "stone", 0, 64, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new LeftClickBlockCommand("request-9", uuid, 0, 64, 0, "UP")), true);
        fixture.dispatcher().handleRequestLine(
            toJson(new RightClickBlockCommand("request-10", uuid, 0, 64, 0, "UP")), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetOpenMenuCommand("request-11", uuid)), true);
        fixture.dispatcher().handleRequestLine(toJson(new ClickMenuSlotCommand("request-12", uuid, 0, "LEFT")), true);
        fixture.dispatcher().handleRequestLine(
            toJson(new DragMenuSlotsCommand("request-13", uuid, "stone", new int[]{0, 1})), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetPlayerMessagesCommand("request-14", uuid)), true);
        fixture.dispatcher().handleRequestLine(toJson(new WaitTicksCommand("request-15", 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetServerTickCommand("request-16")), true);
        fixture.dispatcher().handleRequestLine(
            toJson(new TeleportPlayerCommand("request-17", uuid, "world", 0, 64, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new LoadChunkCommand("request-18", "world", 0, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new UnloadChunkCommand("request-19", "world", 0, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new IsChunkLoadedCommand("request-20", "world", 0, 0)), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetPlayerInventoryCommand("request-21", uuid)), true);
        fixture.dispatcher().handleRequestLine(toJson(new DropItemCommand("request-22", uuid)), true);
        fixture.dispatcher().handleRequestLine(toJson(new RegisterEventListenerCommand("request-23", eventClass)), true);
        fixture.dispatcher().handleRequestLine(toJson(new GetCapturedEventsCommand("request-24", eventClass)), true);
        fixture.dispatcher().handleRequestLine(toJson(new ClearCapturedEventsCommand("request-25", eventClass)), true);
        fixture.dispatcher().handleRequestLine(
            toJson(new UnregisterEventListenerCommand("request-26", eventClass)), true);

        // verify
        verify(fixture.worldActions()).handleNewWorld(any(NewWorldCommand.class));
        verify(fixture.worldActions()).handleExecuteCommand(any(ExecuteCommandCommand.class));
        verify(fixture.worldActions()).handleBlockType(any(BlockTypeCommand.class));
        verify(fixture.worldActions()).handleSetBlock(any(SetBlockCommand.class));
        verify(fixture.playerActions()).handleCreatePlayer(any(CreatePlayerCommand.class));
        verify(fixture.playerActions()).handleRemovePlayer(any(RemovePlayerCommand.class));
        verify(fixture.playerActions()).handleExecutePlayerCommand(any(ExecutePlayerCommandCommand.class));
        verify(fixture.playerActions()).handlePlacePlayerBlock(any(PlacePlayerBlockCommand.class));
        verify(fixture.playerActions()).handleLeftClickBlock(any(LeftClickBlockCommand.class));
        verify(fixture.playerActions()).handleRightClickBlock(any(RightClickBlockCommand.class));
        verify(fixture.menuActions()).handleGetOpenMenu(any(GetOpenMenuCommand.class));
        verify(fixture.menuActions()).handleClickMenuSlot(any(ClickMenuSlotCommand.class));
        verify(fixture.menuActions()).handleDragMenuSlots(any(DragMenuSlotsCommand.class));
        verify(fixture.playerActions()).handleGetPlayerMessages(any(GetPlayerMessagesCommand.class));
        verify(fixture.worldActions()).handleWaitTicks(any(WaitTicksCommand.class));
        verify(fixture.worldActions()).handleGetServerTick(any(GetServerTickCommand.class));
        verify(fixture.playerActions()).handleTeleportPlayer(any(TeleportPlayerCommand.class));
        verify(fixture.worldActions()).handleLoadChunk(any(LoadChunkCommand.class));
        verify(fixture.worldActions()).handleUnloadChunk(any(UnloadChunkCommand.class));
        verify(fixture.worldActions()).handleIsChunkLoaded(any(IsChunkLoadedCommand.class));
        verify(fixture.playerActions()).handleGetPlayerInventory(any(GetPlayerInventoryCommand.class));
        verify(fixture.playerActions()).handleDropItem(any(DropItemCommand.class));
        verify(fixture.eventCapture()).registerListener(eventClass);
        verify(fixture.eventCapture()).getCapturedEvents(eventClass);
        verify(fixture.eventCapture()).clearCapturedEvents(eventClass);
        verify(fixture.eventCapture()).unregisterListener(eventClass);
    }

    @Test
    void handleRequestLine_shouldReturnInvalidArgumentWhenEventClassIsNotValid()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        doThrow(new IllegalArgumentException("Class 'java.lang.String' is not a Bukkit Event."))
            .when(fixture.eventCapture()).registerListener("java.lang.String");
        final String requestLine = toJson(new RegisterEventListenerCommand("request-event", "java.lang.String"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(result.response().success()).isFalse();
        assertThat(result.response().errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(result.response().errorMessage()).contains("not a Bukkit Event");
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
        assertThat(result.response().requestId()).isEqualTo("unknown");
        assertThat(result.response().success()).isFalse();
        assertThat(result.response().errorCode()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void handleRequestLine_shouldRejectNonHandshakeRequestBeforeHandshakeCompletes()
        throws Exception
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("token", 1, "");
        final String requestLine = toJson(new MainWorldCommand("request-1"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(requestLine, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(result.response().requestId()).isEqualTo("request-1");
        assertThat(result.response().success()).isFalse();
        assertThat(result.response().errorCode()).isEqualTo("HANDSHAKE_REQUIRED");
    }

    @Test
    void handleRequestLine_shouldReturnAuthFailedWhenHandshakeTokenDoesNotMatch()
        throws Exception
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("expected-token", 1, "");
        final String requestLine = toJson(new HandshakeCommand("request-2", "wrong-token", 1, ""));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(requestLine, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(result.response().requestId()).isEqualTo("request-2");
        assertThat(result.response().success()).isFalse();
        assertThat(result.response().errorCode()).isEqualTo("AUTH_FAILED");
    }

    @Test
    void handleRequestLine_shouldReturnProtocolMismatchWhenProtocolVersionDiffers()
        throws Exception
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("expected-token", 1, "");
        final String requestLine = toJson(new HandshakeCommand("request-3", "expected-token", 0, ""));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(requestLine, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(result.response().requestId()).isEqualTo("request-3");
        assertThat(result.response().success()).isFalse();
        assertThat(result.response().errorCode()).isEqualTo("PROTOCOL_MISMATCH");
        assertThat(result.response().errorMessage()).contains("expected=1").contains("actual=0");
        assertThat(result.response().data())
            .containsEntry("expectedProtocolVersion", "1")
            .containsEntry("actualProtocolVersion", "0");
    }

    @Test
    void handleRequestLine_shouldReturnAgentShaMismatchWhenExpectedShaDoesNotMatch()
        throws Exception
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("expected-token", 1, "expected-sha");
        final String requestLine = toJson(new HandshakeCommand("request-4", "expected-token", 1, "actual-sha"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(requestLine, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(result.response().requestId()).isEqualTo("request-4");
        assertThat(result.response().success()).isFalse();
        assertThat(result.response().errorCode()).isEqualTo("AGENT_SHA_MISMATCH");
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
        final AgentEventCapture eventCapture = mock();

        return new AgentRequestDispatcher(
            objectMapper,
            worldActions,
            playerActions,
            menuActions,
            eventCapture,
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
        final AgentEventCapture eventCapture = mock();
        final AgentRequestDispatcher dispatcher = new AgentRequestDispatcher(
            objectMapper,
            worldActions,
            playerActions,
            menuActions,
            eventCapture,
            Logger.getLogger(AgentRequestDispatcherTest.class.getName()),
            "token",
            1,
            ""
        );
        return new DispatcherFixture(dispatcher, worldActions, playerActions, menuActions, eventCapture);
    }

    private record DispatcherFixture(
        AgentRequestDispatcher dispatcher,
        AgentWorldActions worldActions,
        AgentPlayerActions playerActions,
        AgentMenuActions menuActions,
        AgentEventCapture eventCapture)
    {
    }

    private static String toJson(IAgentCommand command)
        throws Exception
    {
        return OBJECT_MAPPER.writeValueAsString(command);
    }
}
