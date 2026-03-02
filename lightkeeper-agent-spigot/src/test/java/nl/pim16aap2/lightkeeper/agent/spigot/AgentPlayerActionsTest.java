package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

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

    private static AgentPlayerActions createPlayerActions()
    {
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        final AgentSyntheticPlayerStore playerStore = new AgentSyntheticPlayerStore();
        final ObjectMapper objectMapper = new ObjectMapper();
        final AgentMenuActions menuActions = new AgentMenuActions(
            mainThreadExecutor,
            new AgentMenuController(),
            playerStore,
            objectMapper
        );
        final IBotPlayerNmsAdapter nmsAdapter = mock();

        return new AgentPlayerActions(
            plugin,
            mainThreadExecutor,
            playerStore,
            menuActions,
            objectMapper,
            nmsAdapter
        );
    }
}
