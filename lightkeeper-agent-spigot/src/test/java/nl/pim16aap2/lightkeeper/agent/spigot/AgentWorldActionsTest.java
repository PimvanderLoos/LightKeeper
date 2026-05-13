package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommandCommand;
import nl.pim16aap2.lightkeeper.protocol.GetServerTickCommand;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoadedCommand;
import nl.pim16aap2.lightkeeper.protocol.LoadChunkCommand;
import nl.pim16aap2.lightkeeper.protocol.NewWorldCommand;
import nl.pim16aap2.lightkeeper.protocol.SetBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunkCommand;
import nl.pim16aap2.lightkeeper.protocol.WaitTicksCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    void detectServerPlatform_shouldTreatCraftBukkitServerClassAsSpigot()
    {
        // setup
        final String serverClassName = "org.bukkit.craftbukkit.v1_21_R7.CraftServer";

        // execute
        final String result = AgentPlatformDetector.detect(
            "CraftBukkit",
            "1.21.11-R0.1-SNAPSHOT",
            serverClassName,
            false
        );

        // verify
        assertThat(result).isEqualTo("SPIGOT");
    }

    @Test
    void detectServerPlatform_shouldPreferPaperWhenPaperClassIsPresent()
    {
        // setup
        final String serverClassName = "org.bukkit.craftbukkit.v1_21_R7.CraftServer";

        // execute
        final String result = AgentPlatformDetector.detect(
            "CraftBukkit",
            "1.21.11-R0.1-SNAPSHOT",
            serverClassName,
            true
        );

        // verify
        assertThat(result).isEqualTo("PAPER");
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

    @Test
    void handleLoadChunk_shouldLoadRequestedChunk()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleLoadChunk(new LoadChunkCommand("request-load", "world", 2, 3));
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("loaded", "true");
        verify(world).loadChunk(2, 3, true);
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
            response = worldActions.handleUnloadChunk(new UnloadChunkCommand("request-unload", "world", 4, 5));
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("unloaded", "false");
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
            response = worldActions.handleIsChunkLoaded(new IsChunkLoadedCommand("request-loaded", "world", 6, 7));
        }

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("loaded", "true");
    }

    @Test
    void detectServerPlatform_shouldPreferPaperClassSignal()
    {
        // execute
        final String platform = AgentPlatformDetector.detect(
            "CraftBukkit",
            "git-Spigot-abc",
            "org.bukkit.craftbukkit.CraftServer",
            true
        );

        // verify
        assertThat(platform).isEqualTo("PAPER");
    }

    @Test
    void detectServerPlatform_shouldMapCraftBukkitServerToSpigot()
    {
        // execute
        final String platform = AgentPlatformDetector.detect(
            "CraftBukkit",
            "1.21.11-R0.1-SNAPSHOT",
            "org.bukkit.craftbukkit.CraftServer",
            false
        );

        // verify
        assertThat(platform).isEqualTo("SPIGOT");
    }

    @Test
    void detectServerPlatform_shouldReturnUnknownWhenNeitherPaperNorSpigotIdentifiersFound()
    {
        // execute
        final String platform = AgentPlatformDetector.detect(
            "SomeOtherServer",
            "1.21.11-R0.1-SNAPSHOT",
            "com.someother.SomeServer",
            false
        );

        // verify
        assertThat(platform).isEqualTo("UNKNOWN");
    }

    @Test
    void detectServerPlatform_shouldReturnPaperWhenBukkitNameContainsPaper()
    {
        // execute
        final String platform = AgentPlatformDetector.detect(
            "Paper",
            "1.21.11-R0.1-SNAPSHOT",
            "com.someserver.SomeServer",
            false
        );

        // verify
        assertThat(platform).isEqualTo("PAPER");
    }

    @Test
    void detectServerPlatform_shouldReturnSpigotWhenVersionContainsSpigot()
    {
        // execute
        final String platform = AgentPlatformDetector.detect(
            "CraftBukkit",
            "git-Spigot-1.21.11-R0.1",
            "com.someserver.SomeServer",
            false
        );

        // verify
        assertThat(platform).isEqualTo("SPIGOT");
    }

    @Test
    void handleWaitTicks_shouldReturnInterruptedWhenThreadIsInterrupted()
        throws Exception
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(0L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);
        final java.util.concurrent.atomic.AtomicReference<AgentResponse>
            responseRef = new java.util.concurrent.atomic.AtomicReference<>();

        // execute - run in a thread and interrupt it while waiting for ticks
        final Thread waitingThread = Thread.ofPlatform().unstarted(() ->
            responseRef.set(worldActions.handleWaitTicks(new WaitTicksCommand("request-interrupted", 1000)))
        );
        waitingThread.start();
        Thread.sleep(50);
        waitingThread.interrupt();
        waitingThread.join(2000);

        // verify
        final AgentResponse response = responseRef.get();
        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INTERRUPTED");
    }

    @Test
    void handleExecuteCommand_shouldReturnErrorWhenCommandIsBlank()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());

        // execute
        final AgentResponse response =
            worldActions.handleExecuteCommand(new ExecuteCommandCommand("request-blank", "CONSOLE", "   "));

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("command");
    }

    @Test
    void handleExecuteCommand_shouldReturnErrorWhenSourceIsNotConsole()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());

        // execute
        final AgentResponse response =
            worldActions.handleExecuteCommand(new ExecuteCommandCommand("request-source", "PLAYER", "help"));

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("UNSUPPORTED_SOURCE");
    }

    @Test
    void handleNewWorld_shouldReturnErrorWhenWorldNameIsBlank()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());

        // execute
        final AgentResponse response =
            worldActions.handleNewWorld(new NewWorldCommand("request-blank-world", "   ", "NORMAL", "NORMAL", 0L));

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("worldName");
    }

    @Test
    void handleSetBlock_shouldReturnErrorWhenMaterialIsBlank()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());

        // execute
        final AgentResponse response =
            worldActions.handleSetBlock(new SetBlockCommand("request-blank-mat", "world", 0, 64, 0, "   "));

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void handleSetBlock_shouldReturnErrorWhenMaterialIsUnknown()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());

        // execute
        final AgentResponse response;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Material> materialMockedStatic = mockStatic(Material.class))
        {
            materialMockedStatic.when(() -> Material.matchMaterial(anyString(), eq(true))).thenReturn(null);
            response = worldActions.handleSetBlock(
                new SetBlockCommand("request-unknown-mat", "world", 0, 64, 0, "not_a_material")
            );
        }

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("Unknown material");
    }

    private static AgentWorldActions createWorldActions(AtomicLong tickCounter)
    {
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        return new AgentWorldActions(plugin, mainThreadExecutor, tickCounter);
    }
}
