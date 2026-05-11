package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
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
        final AgentResponse response = worldActions.handleGetServerTick("request-1");

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
        final Map<String, String> arguments = Map.of("ticks", "-1");

        // execute
        final AgentResponse response = worldActions.handleWaitTicks("request-2", arguments);

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
        final Map<String, String> arguments = Map.of("ticks", "0");

        // execute
        final AgentResponse response = worldActions.handleWaitTicks("request-3", arguments);

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("startTick", "9");
        assertThat(response.data()).containsEntry("endTick", "9");
    }

    @Test
    void handleLoadChunk_shouldLoadRequestedChunk()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        final Chunk chunk = mock();
        when(world.getChunkAt(2, 3)).thenReturn(chunk);

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleLoadChunk("request-load", Map.of(
                "worldName", "world",
                "x", "2",
                "z", "3"
            ));
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("loaded", "true");
        verify(chunk).load();
    }

    @Test
    void handleUnloadChunk_shouldReturnWorldUnloadResult()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        when(world.unloadChunk(4, 5)).thenReturn(false);

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleUnloadChunk("request-unload", Map.of(
                "worldName", "world",
                "x", "4",
                "z", "5"
            ));
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("success", "false");
    }

    @Test
    void handleIsChunkLoaded_shouldReturnWorldLoadedResult()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        when(world.isChunkLoaded(6, 7)).thenReturn(true);

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleIsChunkLoaded("request-loaded", Map.of(
                "worldName", "world",
                "x", "6",
                "z", "7"
            ));
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("loaded", "true");
    }

    private static AgentWorldActions createWorldActions(AtomicLong tickCounter)
    {
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        return new AgentWorldActions(plugin, mainThreadExecutor, tickCounter);
    }
}
