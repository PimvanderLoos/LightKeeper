package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommand;
import nl.pim16aap2.lightkeeper.protocol.GetServerTick;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoaded;
import nl.pim16aap2.lightkeeper.protocol.LoadChunk;
import nl.pim16aap2.lightkeeper.protocol.NewWorld;
import nl.pim16aap2.lightkeeper.protocol.SetBlock;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunk;
import nl.pim16aap2.lightkeeper.protocol.WaitTicks;
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
        final GetServerTick.Response response = worldActions.handleGetServerTick(new GetServerTick.Command("request-1"));

        // verify
        assertThat(response.requestId()).isEqualTo("request-1");
        assertThat(response.tick()).isEqualTo(17L);
    }

    @Test
    void handleWaitTicks_shouldThrowWhenTicksAreNegative()
    {
        // setup + execute + verify
        assertThatThrownBy(() -> new WaitTicks.Command("request-2", -1))
            .isInstanceOf(IllegalArgumentException.class)
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

    @Test
    void handleLoadChunk_shouldReturnWorldLoadResult()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        when(world.loadChunk(2, 3, true)).thenReturn(true);

        // execute
        final LoadChunk.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleLoadChunk(new LoadChunk.Command("request-load", "world", 2, 3));
        }

        // verify
        assertThat(response.loaded()).isTrue();
        verify(world).loadChunk(2, 3, true);
    }

    @Test
    void handleLoadChunk_shouldReturnFalseWhenLoadFails()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        when(world.loadChunk(2, 3, true)).thenReturn(false);

        // execute
        final LoadChunk.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleLoadChunk(new LoadChunk.Command("request-load", "world", 2, 3));
        }

        // verify
        assertThat(response.loaded()).isFalse();
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
        final UnloadChunk.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleUnloadChunk(new UnloadChunk.Command("request-unload", "world", 4, 5));
        }

        // verify
        assertThat(response.unloaded()).isFalse();
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
        final IsChunkLoaded.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleIsChunkLoaded(new IsChunkLoaded.Command("request-loaded", "world", 6, 7));
        }

        // verify
        assertThat(response.loaded()).isTrue();
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
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(0L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);
        final WaitTicks.Command command = new WaitTicks.Command("request-interrupted", 1000);

        // execute + verify
        Thread.currentThread().interrupt();
        try
        {
            assertThatThrownBy(() -> worldActions.handleWaitTicks(command))
                .isInstanceOf(AgentProtocolException.class)
                .extracting(exception -> ((AgentProtocolException) exception).errorCode())
                .isEqualTo(AgentErrorCode.INTERRUPTED);
        }
        finally
        {
            Thread.interrupted();
        }
    }

    @Test
    void executeCommandCommand_shouldRejectBlankCommand()
    {
        // setup + execute + verify — validation is enforced by the command's compact constructor
        assertThatThrownBy(() ->
            new ExecuteCommand.Command("request-blank", CommandSource.CONSOLE, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("command");
    }

    @Test
    void newWorldCommand_shouldRejectBlankWorldName()
    {
        // setup + execute + verify — validation is enforced by the command's compact constructor
        assertThatThrownBy(() ->
            new NewWorld.Command("request-blank-world", "   ", "NORMAL", "NORMAL", 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("worldName");
    }

    @Test
    void setBlockCommand_shouldRejectBlankMaterial()
    {
        // setup + execute + verify — validation is enforced by the command's compact constructor
        assertThatThrownBy(() ->
            new SetBlock.Command("request-blank-mat", "world", 0, 64, 0, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("material");
    }

    @Test
    void handleSetBlock_shouldThrowWhenMaterialIsUnknown()
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());

        final SetBlock.Command command =
            new SetBlock.Command("request-unknown-mat", "world", 0, 64, 0, "not_a_material");

        // execute + verify
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Material> materialMockedStatic = mockStatic(Material.class))
        {
            materialMockedStatic.when(() -> Material.matchMaterial(anyString(), eq(true))).thenReturn(null);
            assertThatThrownBy(() -> worldActions.handleSetBlock(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown material");
        }
    }

    private static AgentWorldActions createWorldActions(AtomicLong tickCounter)
    {
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        return new AgentWorldActions(plugin, mainThreadExecutor, tickCounter);
    }
}
