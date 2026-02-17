package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentRequestDispatcherTest
{
    @Test
    void handleRequestLine_shouldCompleteHandshakeWhenTokenAndProtocolMatch()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        final String requestLine = createRequestLine(
            "request-0",
            AgentAction.HANDSHAKE,
            Map.of("token", "token", "protocolVersion", "v1.1", "agentSha256", "")
        );

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
        when(fixture.worldActions().handleMainWorld("request-world"))
            .thenReturn(AgentResponses.successResponse("request-world", Map.of("world", "world")));
        final String requestLine = createRequestLine("request-world", AgentAction.MAIN_WORLD, Map.of());

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result =
            fixture.dispatcher().handleRequestLine(requestLine, true);

        // verify
        assertThat(result.handshakeCompleted()).isTrue();
        assertThat(result.response().success()).isTrue();
        verify(fixture.worldActions()).handleMainWorld("request-world");
    }

    @Test
    void handleRequestLine_shouldReturnRequestFailedWhenActionThrowsException()
        throws Exception
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        when(fixture.worldActions().handleMainWorld("request-fail"))
            .thenThrow(new IllegalStateException("boom"));
        final String requestLine = createRequestLine("request-fail", AgentAction.MAIN_WORLD, Map.of());

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
        when(fixture.worldActions().handleNewWorld(eq("request-1"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-1", Map.of()));
        when(fixture.worldActions().handleExecuteCommand(eq("request-2"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-2", Map.of()));
        when(fixture.worldActions().handleBlockType(eq("request-3"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-3", Map.of()));
        when(fixture.worldActions().handleSetBlock(eq("request-4"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-4", Map.of()));
        when(fixture.playerActions().handleCreatePlayer(eq("request-5"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-5", Map.of()));
        when(fixture.playerActions().handleRemovePlayer(eq("request-6"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-6", Map.of()));
        when(fixture.playerActions().handleExecutePlayerCommand(eq("request-7"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-7", Map.of()));
        when(fixture.playerActions().handlePlacePlayerBlock(eq("request-8"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-8", Map.of()));
        when(fixture.menuActions().handleGetOpenMenu(eq("request-9"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-9", Map.of()));
        when(fixture.menuActions().handleClickMenuSlot(eq("request-10"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-10", Map.of()));
        when(fixture.menuActions().handleDragMenuSlots(eq("request-11"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-11", Map.of()));
        when(fixture.playerActions().handleGetPlayerMessages(eq("request-12"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-12", Map.of()));
        when(fixture.worldActions().handleWaitTicks(eq("request-13"), anyMap()))
            .thenReturn(AgentResponses.successResponse("request-13", Map.of()));
        when(fixture.worldActions().handleGetServerTick("request-14"))
            .thenReturn(AgentResponses.successResponse("request-14", Map.of()));

        // execute
        fixture.dispatcher().handleRequestLine(createRequestLine("request-1", AgentAction.NEW_WORLD, Map.of()), true);
        fixture.dispatcher()
            .handleRequestLine(createRequestLine("request-2", AgentAction.EXECUTE_COMMAND, Map.of()), true);
        fixture.dispatcher().handleRequestLine(createRequestLine("request-3", AgentAction.BLOCK_TYPE, Map.of()), true);
        fixture.dispatcher().handleRequestLine(createRequestLine("request-4", AgentAction.SET_BLOCK, Map.of()), true);
        fixture.dispatcher()
            .handleRequestLine(createRequestLine("request-5", AgentAction.CREATE_PLAYER, Map.of()), true);
        fixture.dispatcher()
            .handleRequestLine(createRequestLine("request-6", AgentAction.REMOVE_PLAYER, Map.of()), true);
        fixture.dispatcher().handleRequestLine(
            createRequestLine("request-7", AgentAction.EXECUTE_PLAYER_COMMAND, Map.of()),
            true
        );
        fixture.dispatcher().handleRequestLine(
            createRequestLine("request-8", AgentAction.PLACE_PLAYER_BLOCK, Map.of()),
            true
        );
        fixture.dispatcher()
            .handleRequestLine(createRequestLine("request-9", AgentAction.GET_OPEN_MENU, Map.of()), true);
        fixture.dispatcher()
            .handleRequestLine(createRequestLine("request-10", AgentAction.CLICK_MENU_SLOT, Map.of()), true);
        fixture.dispatcher()
            .handleRequestLine(createRequestLine("request-11", AgentAction.DRAG_MENU_SLOTS, Map.of()), true);
        fixture.dispatcher().handleRequestLine(
            createRequestLine("request-12", AgentAction.GET_PLAYER_MESSAGES, Map.of()),
            true
        );
        fixture.dispatcher()
            .handleRequestLine(createRequestLine("request-13", AgentAction.WAIT_TICKS, Map.of()), true);
        fixture.dispatcher()
            .handleRequestLine(createRequestLine("request-14", AgentAction.GET_SERVER_TICK, Map.of()), true);

        // verify
        verify(fixture.worldActions()).handleNewWorld(eq("request-1"), anyMap());
        verify(fixture.worldActions()).handleExecuteCommand(eq("request-2"), anyMap());
        verify(fixture.worldActions()).handleBlockType(eq("request-3"), anyMap());
        verify(fixture.worldActions()).handleSetBlock(eq("request-4"), anyMap());
        verify(fixture.playerActions()).handleCreatePlayer(eq("request-5"), anyMap());
        verify(fixture.playerActions()).handleRemovePlayer(eq("request-6"), anyMap());
        verify(fixture.playerActions()).handleExecutePlayerCommand(eq("request-7"), anyMap());
        verify(fixture.playerActions()).handlePlacePlayerBlock(eq("request-8"), anyMap());
        verify(fixture.menuActions()).handleGetOpenMenu(eq("request-9"), anyMap());
        verify(fixture.menuActions()).handleClickMenuSlot(eq("request-10"), anyMap());
        verify(fixture.menuActions()).handleDragMenuSlots(eq("request-11"), anyMap());
        verify(fixture.playerActions()).handleGetPlayerMessages(eq("request-12"), anyMap());
        verify(fixture.worldActions()).handleWaitTicks(eq("request-13"), anyMap());
        verify(fixture.worldActions()).handleGetServerTick("request-14");
    }

    @Test
    void delegationMethods_shouldForwardToActionHandlers()
    {
        // setup
        final DispatcherFixture fixture = createDispatcherFixture();
        final Player player = mock();
        final InventoryClickEvent inventoryClickEvent = mock();

        // execute
        fixture.dispatcher().openMainMenu(player);
        fixture.dispatcher().onInventoryClick(inventoryClickEvent);
        fixture.dispatcher().cleanupSyntheticPlayers();

        // verify
        verify(fixture.menuActions()).openMainMenu(player);
        verify(fixture.menuActions()).onInventoryClick(inventoryClickEvent);
        verify(fixture.playerActions()).cleanupSyntheticPlayers();
    }

    @Test
    void handleRequestLine_shouldReturnInvalidRequestWhenJsonIsMalformed()
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("token", "v1.1", "");
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
        final AgentRequestDispatcher dispatcher = createDispatcher("token", "v1.1", "");
        final String requestLine = createRequestLine("request-1", AgentAction.MAIN_WORLD, Map.of());

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
        final AgentRequestDispatcher dispatcher = createDispatcher("expected-token", "v1.1", "");
        final String requestLine = createRequestLine(
            "request-2",
            AgentAction.HANDSHAKE,
            Map.of("token", "wrong-token", "protocolVersion", "v1.1")
        );

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
        final AgentRequestDispatcher dispatcher = createDispatcher("expected-token", "v1.1", "");
        final String requestLine = createRequestLine(
            "request-3",
            AgentAction.HANDSHAKE,
            Map.of("token", "expected-token", "protocolVersion", "v1.0")
        );

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(requestLine, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(result.response().requestId()).isEqualTo("request-3");
        assertThat(result.response().success()).isFalse();
        assertThat(result.response().errorCode()).isEqualTo("PROTOCOL_MISMATCH");
    }

    @Test
    void handleRequestLine_shouldReturnAgentShaMismatchWhenExpectedShaDoesNotMatch()
        throws Exception
    {
        // setup
        final AgentRequestDispatcher dispatcher = createDispatcher("expected-token", "v1.1", "expected-sha");
        final String requestLine = createRequestLine(
            "request-4",
            AgentAction.HANDSHAKE,
            Map.of(
                "token", "expected-token",
                "protocolVersion", "v1.1",
                "agentSha256", "actual-sha")
        );

        // execute
        final AgentRequestDispatcher.RequestDispatchResult result = dispatcher.handleRequestLine(requestLine, false);

        // verify
        assertThat(result.handshakeCompleted()).isFalse();
        assertThat(result.response().requestId()).isEqualTo("request-4");
        assertThat(result.response().success()).isFalse();
        assertThat(result.response().errorCode()).isEqualTo("AGENT_SHA_MISMATCH");
    }

    private static AgentRequestDispatcher createDispatcher(String authToken, String protocolVersion, String expectedSha)
    {
        final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        final AgentSyntheticPlayerStore playerStore = new AgentSyntheticPlayerStore();
        final AgentMenuActions menuActions = new AgentMenuActions(
            mainThreadExecutor,
            new AgentMenuController(),
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
            menuActions,
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
            "v1.1",
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

    private static String createRequestLine(String requestId, AgentAction action, Map<String, String> arguments)
        throws Exception
    {
        return new ObjectMapper().writeValueAsString(new AgentRequest(requestId, action, arguments));
    }
}
