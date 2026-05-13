package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.GetServerTickCommand;
import nl.pim16aap2.lightkeeper.protocol.WaitTicksCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentWorldActionsTest
{
    @Test
    void incrementTick_shouldIncreaseTickCounter()
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(5L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);

        // execute
        worldActions.incrementTick();

        // verify
        assertThat(tickCounter.get()).isEqualTo(6L);
    }

    @Test
    void handleGetServerTick_shouldReturnCurrentTickValue()
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(17L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);

        // execute
        final AgentResponse response = worldActions.handleGetServerTick(new GetServerTickCommand("request-1"));

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("tick", "17");
    }

    @Test
    void handleWaitTicks_shouldReturnErrorWhenTicksAreNegative()
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(3L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);

        // execute
        final AgentResponse response = worldActions.handleWaitTicks(new WaitTicksCommand("request-2", -1));

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void handleWaitTicks_shouldReturnStartAndEndTickWhenTicksAreZero()
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(9L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);

        // execute
        final AgentResponse response = worldActions.handleWaitTicks(new WaitTicksCommand("request-3", 0));

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("startTick", "9");
        assertThat(response.data()).containsEntry("endTick", "9");
    }

    private static AgentWorldActions createWorldActions(AtomicLong tickCounter)
    {
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        return new AgentWorldActions(plugin, mainThreadExecutor, tickCounter);
    }
}
