package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentRequestDispatcherTest
{
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

    private static String createRequestLine(String requestId, AgentAction action, Map<String, String> arguments)
        throws Exception
    {
        return new ObjectMapper().writeValueAsString(new AgentRequest(requestId, action, arguments));
    }
}
