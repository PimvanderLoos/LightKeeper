package nl.pim16aap2.lightkeeper.agent.spigot;

import tools.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.ClickMenuSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentMenuActionsTest
{
    @Test
    void handleClickMenuSlot_shouldThrowWhenSlotIsNegative()
        throws Exception
    {
        // setup
        final AgentMenuActions menuActions = createMenuActions();
        final ClickMenuSlot.Command command =
            new ClickMenuSlot.Command("request-1", UUID.randomUUID(), -1);

        // execute + verify
        assertThatThrownBy(() -> menuActions.handleClickMenuSlot(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("slot");
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
