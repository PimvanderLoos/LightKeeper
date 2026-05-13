package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.ClickMenuSlotCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentMenuActionsTest
{
    @Test
    void handleClickMenuSlot_shouldReturnErrorWhenSlotIsNegative()
        throws Exception
    {
        // setup
        final AgentMenuActions menuActions = createMenuActions();
        final ClickMenuSlotCommand command =
            new ClickMenuSlotCommand("request-1", UUID.randomUUID(), -1, "LEFT");

        // execute
        final AgentResponse response = menuActions.handleClickMenuSlot(command);

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("slot");
    }

    private static AgentMenuActions createMenuActions()
    {
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        final AgentSyntheticPlayerStore playerStore = new AgentSyntheticPlayerStore();
        final ObjectMapper objectMapper = new ObjectMapper();

        return new AgentMenuActions(
            mainThreadExecutor,
            playerStore,
            objectMapper
        );
    }
}
