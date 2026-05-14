package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import nl.pim16aap2.lightkeeper.protocol.GetServerTick;
import nl.pim16aap2.lightkeeper.protocol.WaitTicks;
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
        final GetServerTick.Response response = worldActions.handleGetServerTick(new GetServerTick.Command("request-1"));

        // verify
        assertThat(response.requestId()).isEqualTo("request-1");
        assertThat(response.tick()).isEqualTo(17L);
    }

    @Test
    void handleWaitTicks_shouldThrowProtocolExceptionWhenTicksAreNegative()
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(3L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);

        // execute + verify
        assertThatThrownBy(() -> worldActions.handleWaitTicks(new WaitTicks.Command("request-2", -1)))
            .isInstanceOf(AgentProtocolException.class)
            .hasMessageContaining("ticks");
    }

    @Test
    void handleWaitTicks_shouldReturnStartAndEndTickWhenTicksAreZero()
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(9L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);

        // execute
        final WaitTicks.Response response = worldActions.handleWaitTicks(new WaitTicks.Command("request-3", 0));

        // verify
        assertThat(response.requestId()).isEqualTo("request-3");
        assertThat(response.startTick()).isEqualTo(9L);
        assertThat(response.endTick()).isEqualTo(9L);
    }

    private static AgentWorldActions createWorldActions(AtomicLong tickCounter)
    {
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        return new AgentWorldActions(plugin, mainThreadExecutor, tickCounter);
    }
}
