package nl.pim16aap2.lightkeeper.agent.spigot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolMapper;
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
    private static final ObjectMapper OBJECT_MAPPER = AgentProtocolMapper.create();

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
            .thenReturn(new MainWorld.Response("world"));
        final String requestLine = toJson(new MainWorld.Command("request-world"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(result.handshakeCompleted()).isTrue();
        assertThat(isSuccess(result.responseJson())).isTrue();
        assertThat(OBJECT_MAPPER.readTree(result.responseJson()).path("requestId").asString())
            .isEqualTo("request-world");
        verify(fixture.worldActions()).handleMainWorld(any(MainWorld.Command.class));
    }

    @Test
    void handleRequestLine_shouldRejectUnknownAction()
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        final String requestLine = "{\"action\":\"BOGUS\",\"requestId\":\"request-unknown\"}";

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(requestId(result.responseJson())).isEqualTo("request-unknown");
        assertThat(errorCode(result.responseJson())).isEqualTo("INVALID_REQUEST");
        assertThat(isSuccess(result.responseJson())).isFalse();
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
    void handleRequestLine_shouldReturnInvalidArgumentWhenActionThrowsIllegalArgumentException()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.playerStateActions().handleGetPlayerChatComponents(any()))
            .thenThrow(new IllegalArgumentException("Invalid UUID string: bad-uuid"));
        final String requestLine = toJson(
            new GetPlayerChatComponents.Command(
                "request-invalid", UUID.randomUUID()));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("INVALID_ARGUMENT");
        assertThat(errorMessage(result.responseJson())).contains("bad-uuid");
        assertThat(result.handshakeCompleted()).isTrue();
    }

    @Test
    void handleRequestLine_shouldReturnInvalidArgumentWhenActionThrowsValidationException()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.playerStateActions().handleGetPlayerMessages(any()))
            .thenThrow(new IllegalArgumentException("Invalid UUID string: not-a-uuid"));
        final String requestLine = toJson(
            new GetPlayerMessages.Command("request-invalid-uuid", UUID.randomUUID()));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("INVALID_ARGUMENT");
        assertThat(errorMessage(result.responseJson())).contains("Invalid UUID");
        assertThat(result.handshakeCompleted()).isTrue();
    }

    @Test
    void handleRequestLine_shouldReturnInvalidArgumentWhenCompactConstructorRejectsField()
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        // Raw JSON so the invalid field is rejected inside WaitTicks.Command's compact constructor during parsing.
        final String requestLine =
            "{\"action\":\"WAIT_TICKS\",\"requestId\":\"request-negative-ticks\",\"ticks\":-1}";

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("INVALID_ARGUMENT");
        assertThat(errorMessage(result.responseJson())).contains("ticks");
    }

    @Test
    void handleRequestLine_shouldReturnInvalidRequestWhenJsonFieldHasWrongType()
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        // A non-integer protocolVersion is a malformed field (type error), not an argument-validation failure.
        final String requestLine =
            "{\"action\":\"HANDSHAKE\",\"requestId\":\"request-bad-version\",\"token\":\"token\","
                + "\"protocolVersion\":\"not-an-int\",\"agentSha256\":\"\"}";

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, false);

        // verify
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void handleRequestLine_shouldReturnTimeoutWhenHandlerReportsTimeout()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.worldActions().handleWaitTicks(any()))
            .thenThrow(new AgentProtocolException(AgentErrorCode.TIMEOUT, "Server operation did not complete."));
        final String requestLine = toJson(new WaitTicks.Command("request-timeout", 5));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("TIMEOUT");
    }

    @Test
    void handleRequestLine_shouldReturnRequestFailedWhenHandlerThrowsError()
        throws Exception
    {
        // setup — a LinkageError from the reflection-heavy NMS layer must not kill the connection thread
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.worldActions().handleMainWorld(any(MainWorld.Command.class)))
            .thenThrow(new NoClassDefFoundError("nms/Missing"));
        final String requestLine = toJson(new MainWorld.Command("request-error"));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(errorCode(result.responseJson())).isEqualTo("REQUEST_FAILED");
    }

    @Test
    void handleRequestLine_shouldRethrowVirtualMachineError()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.worldActions().handleMainWorld(any(MainWorld.Command.class)))
            .thenThrow(new OutOfMemoryError("boom"));
        final String requestLine = toJson(new MainWorld.Command("request-oom"));

        // execute + verify — an unrecoverable VirtualMachineError must propagate, not be swallowed
        assertThatThrownBy(() -> fixture.dispatcher().handleRequestLine(requestLine, true))
            .isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    void handleRequestLine_shouldAppendCauseWhenProtocolExceptionCarriesOne()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.worldActions().handleWaitTicks(any()))
            .thenThrow(new AgentProtocolException(
                AgentErrorCode.INTERRUPTED, "Interrupted while waiting.",
                new InterruptedException("thread interrupted")));
        final String requestLine = toJson(new WaitTicks.Command("request-interrupted", 5));

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify — the wrapped cause must not be invisible; it is appended to the error message
        assertThat(errorCode(result.responseJson())).isEqualTo("INTERRUPTED");
        assertThat(errorMessage(result.responseJson())).contains("thread interrupted");
    }

    @Test
    void handleRequestLine_shouldDispatchSupportedActionsToTheirHandlers()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        final UUID uuid = UUID.randomUUID();

        when(fixture.worldActions().handleNewWorld(any(NewWorld.Command.class)))
            .thenReturn(new NewWorld.Response("w"));
        when(fixture.worldActions().handleExecuteCommand(any(ExecuteCommand.Command.class)))
            .thenReturn(new ExecuteCommand.Response(true));
        when(fixture.worldActions().handleBlockType(any(BlockType.Command.class)))
            .thenReturn(new BlockType.Response("STONE"));
        when(fixture.worldActions().handleSetBlock(any(SetBlock.Command.class)))
            .thenReturn(new SetBlock.Response("STONE"));
        when(fixture.playerActions().handleCreatePlayer(any(CreatePlayer.Command.class)))
            .thenReturn(new CreatePlayer.Response(uuid, "bot"));
        when(fixture.playerActions().handleRemovePlayer(any(RemovePlayer.Command.class)))
            .thenReturn(new RemovePlayer.Response());
        when(fixture.playerActions().handleExecutePlayerCommand(any(ExecutePlayerCommand.Command.class)))
            .thenReturn(new ExecutePlayerCommand.Response(true));
        when(fixture.playerActions().handlePlacePlayerBlock(any(PlacePlayerBlock.Command.class)))
            .thenReturn(new PlacePlayerBlock.Response("minecraft:stone"));
        when(fixture.playerActions().handleLeftClickBlock(any(LeftClickBlock.Command.class)))
            .thenReturn(new LeftClickBlock.Response(false));
        when(fixture.playerActions().handleRightClickBlock(any(RightClickBlock.Command.class)))
            .thenReturn(new RightClickBlock.Response(false));
        when(fixture.menuActions().handleGetOpenMenu(any(GetOpenMenu.Command.class)))
            .thenReturn(new GetOpenMenu.Response(false, null, java.util.List.of()));
        when(fixture.menuActions().handleClickMenuSlot(any(ClickMenuSlot.Command.class)))
            .thenReturn(new ClickMenuSlot.Response());
        when(fixture.menuActions().handleDragMenuSlots(any(DragMenuSlots.Command.class)))
            .thenReturn(new DragMenuSlots.Response());
        when(fixture.playerStateActions().handleGetPlayerMessages(any(GetPlayerMessages.Command.class)))
            .thenReturn(new GetPlayerMessages.Response(java.util.List.of()));
        when(fixture.worldActions().handleWaitTicks(any(WaitTicks.Command.class)))
            .thenReturn(new WaitTicks.Response(0L, 0L));
        when(fixture.worldActions().handleGetServerTick(any(GetServerTick.Command.class)))
            .thenReturn(new GetServerTick.Response(0L));
        when(fixture.playerActions().handleTeleportPlayer(any(TeleportPlayer.Command.class)))
            .thenReturn(new TeleportPlayer.Response(true));
        when(fixture.worldActions().handleLoadChunk(any(LoadChunk.Command.class)))
            .thenReturn(new LoadChunk.Response(true));
        when(fixture.worldActions().handleUnloadChunk(any(UnloadChunk.Command.class)))
            .thenReturn(new UnloadChunk.Response(true));
        when(fixture.worldActions().handleIsChunkLoaded(any(IsChunkLoaded.Command.class)))
            .thenReturn(new IsChunkLoaded.Response(true));
        when(fixture.playerStateActions().handleGetPlayerInventory(any(GetPlayerInventory.Command.class)))
            .thenReturn(new GetPlayerInventory.Response(java.util.List.of()));
        when(fixture.playerStateActions().handleDropItem(any(DropItem.Command.class)))
            .thenReturn(new DropItem.Response(false));
        when(fixture.eventActions().handleRegisterEventListener(any(RegisterEventListener.Command.class)))
            .thenReturn(new RegisterEventListener.Response());
        when(fixture.eventActions().handleGetCapturedEvents(any(GetCapturedEvents.Command.class)))
            .thenReturn(new GetCapturedEvents.Response(java.util.List.of()));
        when(fixture.eventActions().handleClearCapturedEvents(any(ClearCapturedEvents.Command.class)))
            .thenReturn(new ClearCapturedEvents.Response());
        when(fixture.eventActions().handleUnregisterEventListener(any(UnregisterEventListener.Command.class)))
            .thenReturn(new UnregisterEventListener.Response());
        when(fixture.playerStateActions().handleGetPlayerChatComponents(any(GetPlayerChatComponents.Command.class)))
            .thenReturn(new GetPlayerChatComponents.Response("[]"));
        when(fixture.worldActions().handleGetServerPlatform(any(GetServerPlatform.Command.class)))
            .thenReturn(new GetServerPlatform.Response("SPIGOT"));

        // execute
        dispatchExpectingSuccess(fixture, toJson(new NewWorld.Command("request-1", "w", "NORMAL", "NORMAL", 0L)));
        dispatchExpectingSuccess(fixture, toJson(new ExecuteCommand.Command("request-2", CommandSource.CONSOLE, "time set day")));
        dispatchExpectingSuccess(fixture, toJson(new BlockType.Command("request-3", "world", 0, 64, 0)));
        dispatchExpectingSuccess(fixture, toJson(new SetBlock.Command("request-4", "world", 0, 64, 0, "stone")));
        dispatchExpectingSuccess(fixture, toJson(new CreatePlayer.Command("request-5", "bot", uuid, "world", null, null, null, null, null)));
        dispatchExpectingSuccess(fixture, toJson(new RemovePlayer.Command("request-6", uuid)));
        dispatchExpectingSuccess(fixture, toJson(new ExecutePlayerCommand.Command("request-7", uuid, "gamemode creative")));
        dispatchExpectingSuccess(fixture, toJson(new PlacePlayerBlock.Command("request-8", uuid, "stone", 0, 64, 0)));
        dispatchExpectingSuccess(fixture, toJson(new LeftClickBlock.Command("request-9", uuid, 0, 64, 0, "UP")));
        dispatchExpectingSuccess(fixture, toJson(new RightClickBlock.Command("request-10", uuid, 0, 64, 0, "UP")));
        dispatchExpectingSuccess(fixture, toJson(new GetOpenMenu.Command("request-11", uuid)));
        dispatchExpectingSuccess(fixture, toJson(new ClickMenuSlot.Command("request-12", uuid, 0)));
        dispatchExpectingSuccess(fixture, toJson(new DragMenuSlots.Command("request-13", uuid, "stone", new int[]{0, 1})));
        dispatchExpectingSuccess(fixture, toJson(new GetPlayerMessages.Command("request-14", uuid)));
        dispatchExpectingSuccess(fixture, toJson(new WaitTicks.Command("request-15", 0)));
        dispatchExpectingSuccess(fixture, toJson(new GetServerTick.Command("request-16")));
        dispatchExpectingSuccess(fixture, toJson(new TeleportPlayer.Command("request-17", uuid, "world", 0, 64, 0)));
        dispatchExpectingSuccess(fixture, toJson(new LoadChunk.Command("request-18", "world", 0, 0)));
        dispatchExpectingSuccess(fixture, toJson(new UnloadChunk.Command("request-19", "world", 0, 0)));
        dispatchExpectingSuccess(fixture, toJson(new IsChunkLoaded.Command("request-20", "world", 0, 0)));
        dispatchExpectingSuccess(fixture, toJson(new GetPlayerInventory.Command("request-21", uuid)));
        dispatchExpectingSuccess(fixture, toJson(new DropItem.Command("request-22", uuid)));
        dispatchExpectingSuccess(fixture, toJson(new RegisterEventListener.Command("request-23", "org.bukkit.event.player.PlayerJoinEvent")));
        dispatchExpectingSuccess(fixture, toJson(new GetCapturedEvents.Command("request-24", "org.bukkit.event.player.PlayerJoinEvent")));
        dispatchExpectingSuccess(fixture, toJson(new ClearCapturedEvents.Command("request-25", "org.bukkit.event.player.PlayerJoinEvent")));
        dispatchExpectingSuccess(fixture, toJson(new UnregisterEventListener.Command("request-26", "org.bukkit.event.player.PlayerJoinEvent")));
        dispatchExpectingSuccess(fixture, toJson(new GetPlayerChatComponents.Command("request-27", uuid)));
        dispatchExpectingSuccess(fixture, toJson(new GetServerPlatform.Command("request-28")));

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
        verify(fixture.playerStateActions()).handleGetPlayerMessages(any(GetPlayerMessages.Command.class));
        verify(fixture.worldActions()).handleWaitTicks(any(WaitTicks.Command.class));
        verify(fixture.worldActions()).handleGetServerTick(any(GetServerTick.Command.class));
        verify(fixture.playerActions()).handleTeleportPlayer(any(TeleportPlayer.Command.class));
        verify(fixture.worldActions()).handleLoadChunk(any(LoadChunk.Command.class));
        verify(fixture.worldActions()).handleUnloadChunk(any(UnloadChunk.Command.class));
        verify(fixture.worldActions()).handleIsChunkLoaded(any(IsChunkLoaded.Command.class));
        verify(fixture.playerStateActions()).handleGetPlayerInventory(any(GetPlayerInventory.Command.class));
        verify(fixture.playerStateActions()).handleDropItem(any(DropItem.Command.class));
        verify(fixture.eventActions()).handleRegisterEventListener(any(RegisterEventListener.Command.class));
        verify(fixture.eventActions()).handleGetCapturedEvents(any(GetCapturedEvents.Command.class));
        verify(fixture.eventActions()).handleClearCapturedEvents(any(ClearCapturedEvents.Command.class));
        verify(fixture.eventActions()).handleUnregisterEventListener(any(UnregisterEventListener.Command.class));
        verify(fixture.playerStateActions()).handleGetPlayerChatComponents(any(GetPlayerChatComponents.Command.class));
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
    void handleRequestLine_shouldReturnCorrectRequestIdWhenCommandValidationFails()
    {
        // setup — valid JSON with a known requestId, but name="" fails the CreatePlayer compact constructor
        final AgentRequestDispatcher dispatcher = createDispatcher("token", 1, "");
        final String requestLine = "{\"requestId\":\"req-123\",\"action\":\"CREATE_PLAYER\","
            + "\"name\":\"\",\"uuid\":\"550e8400-e29b-41d4-a716-446655440000\","
            + "\"worldName\":\"world\"}";

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            dispatcher.handleRequestLine(requestLine, true);

        // verify — requestId must be "req-123", not "unknown"; a compact-constructor rejection maps to
        // INVALID_ARGUMENT rather than the generic INVALID_REQUEST parse code
        assertThat(isSuccess(result.responseJson())).isFalse();
        assertThat(requestId(result.responseJson())).isEqualTo("req-123");
        assertThat(errorCode(result.responseJson())).isEqualTo("INVALID_ARGUMENT");
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
        final ObjectMapper objectMapper = AgentProtocolMapper.create();
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        final AgentSyntheticPlayerStore playerStore = new AgentSyntheticPlayerStore();
        final AgentMenuActions menuActions = new AgentMenuActions(
            mainThreadExecutor,
            playerStore
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
            nmsAdapter
        );
        final AgentPlayerStateActions playerStateActions = new AgentPlayerStateActions(
            mainThreadExecutor,
            playerStore,
            objectMapper,
            nmsAdapter
        );
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions eventActions = new AgentEventActions(eventCapture);

        return new AgentRequestDispatcher(
            objectMapper,
            worldActions,
            playerActions,
            playerStateActions,
            menuActions,
            eventActions,
            new AgentRequestDispatcher.Config(
                authToken,
                protocolVersion,
                expectedSha,
                Logger.getLogger(AgentRequestDispatcherTest.class.getName())
            )
        );
    }

    private static DispatcherFixture createDispatcherFixture()
    {
        final ObjectMapper objectMapper = AgentProtocolMapper.create();
        final AgentWorldActions worldActions = mock();
        final AgentPlayerActions playerActions = mock();
        final AgentPlayerStateActions playerStateActions = mock();
        final AgentMenuActions menuActions = mock();
        final AgentEventActions eventActions = mock();
        final AgentRequestDispatcher dispatcher = new AgentRequestDispatcher(
            objectMapper,
            worldActions,
            playerActions,
            playerStateActions,
            menuActions,
            eventActions,
            new AgentRequestDispatcher.Config(
                "token",
                1,
                "",
                Logger.getLogger(AgentRequestDispatcherTest.class.getName())
            )
        );
        return new DispatcherFixture(
            dispatcher, worldActions, playerActions, playerStateActions, menuActions, eventActions);
    }

    private record DispatcherFixture(
        AgentRequestDispatcher dispatcher,
        AgentWorldActions worldActions,
        AgentPlayerActions playerActions,
        AgentPlayerStateActions playerStateActions,
        AgentMenuActions menuActions,
        AgentEventActions eventActions)
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

    /**
     * Dispatches a request line and asserts a success response, so a response record whose serialization throws
     * (which the dispatcher would otherwise swallow into REQUEST_FAILED) is caught rather than hidden.
     */
    private static void dispatchExpectingSuccess(DispatcherFixture fixture, String requestLine)
    {
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);
        assertThat(isSuccess(result.responseJson()))
            .as("dispatch should produce a success response for %s", requestLine)
            .isTrue();
    }

    private static String errorCode(String responseJson)
    {
        try
        {
            return OBJECT_MAPPER.readTree(responseJson).path("errorCode").asString("");
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
            return OBJECT_MAPPER.readTree(responseJson).path("errorMessage").asString("");
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
            return node.path("requestId").asString("unknown");
        }
        catch (Exception exception)
        {
            throw new IllegalArgumentException("Failed to parse response JSON: " + responseJson, exception);
        }
    }
}
