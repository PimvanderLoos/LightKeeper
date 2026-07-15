package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that every deprecated v1 method on {@link ILightkeeperFramework} delegates to its facet replacement.
 *
 * <p>Uses a hand-written facet-stub implementation so the deprecated default methods run against mocked facets;
 * this keeps the delegation contract under test without touching {@code DefaultLightkeeperFramework}.
 */
@SuppressWarnings("removal") // Dedicated delegate test: it intentionally exercises the deprecated-for-removal API.
class DeprecatedDelegateTest
{
    @Test
    void mainWorld_shouldDelegateToWorldsMain()
    {
        // setup
        final IWorlds worlds = mock(IWorlds.class);
        final WorldHandle expected = worldHandle();
        when(worlds.main()).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithWorlds(worlds);

        // execute
        final WorldHandle result = framework.mainWorld();

        // verify
        assertThat(result).isSameAs(expected);
        verify(worlds).main();
    }

    @Test
    void newWorld_shouldDelegateToWorldsCreate()
    {
        // setup
        final IWorlds worlds = mock(IWorlds.class);
        final WorldHandle expected = worldHandle();
        when(worlds.create()).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithWorlds(worlds);

        // execute
        final WorldHandle result = framework.newWorld();

        // verify
        assertThat(result).isSameAs(expected);
        verify(worlds).create();
    }

    @Test
    void newWorld_shouldDelegateToWorldsCreateWithSpec()
    {
        // setup
        final IWorlds worlds = mock(IWorlds.class);
        final WorldSpec spec =
            new WorldSpec("w", WorldSpec.WorldType.NORMAL, WorldSpec.WorldEnvironment.NORMAL, 0L);
        final WorldHandle expected = worldHandle();
        when(worlds.create(spec)).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithWorlds(worlds);

        // execute
        final WorldHandle result = framework.newWorld(spec);

        // verify
        assertThat(result).isSameAs(expected);
        verify(worlds).create(spec);
    }

    @Test
    void newWorldFromTemplate_shouldDelegateToWorldsFromTemplate()
    {
        // setup
        final IWorlds worlds = mock(IWorlds.class);
        final WorldHandle expected = worldHandle();
        when(worlds.fromTemplate("template-a")).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithWorlds(worlds);

        // execute
        final WorldHandle result = framework.newWorldFromTemplate("template-a");

        // verify
        assertThat(result).isSameAs(expected);
        verify(worlds).fromTemplate("template-a");
    }

    @Test
    void buildWorld_shouldDelegateToWorldsBuilder()
    {
        // setup
        final IWorlds worlds = mock(IWorlds.class);
        final IWorldBuilder expected = mock(IWorldBuilder.class);
        when(worlds.builder()).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithWorlds(worlds);

        // execute
        final IWorldBuilder result = framework.buildWorld();

        // verify
        assertThat(result).isSameAs(expected);
        verify(worlds).builder();
    }

    @Test
    void createPlayer_shouldDelegateToBotsJoin()
    {
        // setup
        final IBots bots = mock(IBots.class);
        final WorldHandle world = worldHandle();
        final PlayerHandle expected = playerHandle();
        when(bots.join("bot", world)).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithBots(bots);

        // execute
        final PlayerHandle result = framework.createPlayer("bot", world);

        // verify
        assertThat(result).isSameAs(expected);
        verify(bots).join("bot", world);
    }

    @Test
    void createPlayer_shouldDelegateToBotsJoinWithUuid()
    {
        // setup
        final IBots bots = mock(IBots.class);
        final WorldHandle world = worldHandle();
        final UUID uuid = UUID.randomUUID();
        final PlayerHandle expected = playerHandle();
        when(bots.join("bot", uuid, world)).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithBots(bots);

        // execute
        final PlayerHandle result = framework.createPlayer("bot", uuid, world);

        // verify
        assertThat(result).isSameAs(expected);
        verify(bots).join("bot", uuid, world);
    }

    @Test
    void buildPlayer_shouldDelegateToBotsBuilder()
    {
        // setup
        final IBots bots = mock(IBots.class);
        final IPlayerBuilder expected = mock(IPlayerBuilder.class);
        when(bots.builder()).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithBots(bots);

        // execute
        final IPlayerBuilder result = framework.buildPlayer();

        // verify
        assertThat(result).isSameAs(expected);
        verify(bots).builder();
    }

    @Test
    void executeCommand_shouldDelegateToServerExecuteCommand()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        final CommandResult expected = new CommandResult(true, "ok");
        when(server.executeCommand(CommandSource.CONSOLE, "time set day")).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        final CommandResult result = framework.executeCommand(CommandSource.CONSOLE, "time set day");

        // verify
        assertThat(result).isSameAs(expected);
        verify(server).executeCommand(CommandSource.CONSOLE, "time set day");
    }

    @Test
    void serverOutput_shouldDelegateToServerOutput()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        final List<String> expected = List.of("line one", "line two");
        when(server.output()).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        final List<String> result = framework.serverOutput();

        // verify
        assertThat(result).isSameAs(expected);
        verify(server).output();
    }

    @Test
    void platform_shouldDelegateToServerPlatform()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        when(server.platform()).thenReturn(Platform.PAPER);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        final Platform result = framework.platform();

        // verify
        assertThat(result).isEqualTo(Platform.PAPER);
        verify(server).platform();
    }

    @Test
    void serverDirectory_shouldDelegateToServerDirectory()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        final Path expected = Path.of("/tmp/lightkeeper/server");
        when(server.directory()).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        final Path result = framework.serverDirectory();

        // verify
        assertThat(result).isSameAs(expected);
        verify(server).directory();
    }

    @Test
    void pluginDataDirectory_shouldDelegateToServerPluginDataDirectory()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        final Path expected = Path.of("/tmp/lightkeeper/server/plugins/AnimatedArchitecture");
        when(server.pluginDataDirectory("AnimatedArchitecture")).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        final Path result = framework.pluginDataDirectory("AnimatedArchitecture");

        // verify
        assertThat(result).isSameAs(expected);
        verify(server).pluginDataDirectory("AnimatedArchitecture");
    }

    @Test
    void crashServer_shouldDelegateToServerCrash()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        framework.crashServer();

        // verify
        verify(server).crash();
    }

    @Test
    void stopServer_shouldDelegateToServerStop()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        framework.stopServer();

        // verify
        verify(server).stop();
    }

    @Test
    void startServer_shouldDelegateToServerStart()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        framework.startServer();

        // verify
        verify(server).start();
    }

    @Test
    void restartServer_shouldDelegateToServerRestart()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        framework.restartServer();

        // verify
        verify(server).restart();
    }

    @Test
    void currentServerTick_shouldDelegateToServerCurrentTick()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        when(server.currentTick()).thenReturn(123L);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        final long result = framework.currentServerTick();

        // verify
        assertThat(result).isEqualTo(123L);
        verify(server).currentTick();
    }

    @Test
    void serverErrors_shouldDelegateToServerErrors()
    {
        // setup
        final IServerControl server = mock(IServerControl.class);
        final ServerErrorsHandle expected =
            FrameworkHandleFactory.serverErrorsHandle(mock(IFrameworkGatewayView.class));
        when(server.errors()).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithServer(server);

        // execute
        final ServerErrorsHandle result = framework.serverErrors();

        // verify
        assertThat(result).isSameAs(expected);
        verify(server).errors();
    }

    @Test
    void captureEvents_shouldDelegateToEventsCapture()
    {
        // setup
        final IEvents events = mock(IEvents.class);
        final EventCaptureHandle expected = FrameworkHandleFactory.eventCaptureHandle(
            mock(IFrameworkGatewayView.class), "org.bukkit.event.player.PlayerJoinEvent");
        when(events.capture("org.bukkit.event.player.PlayerJoinEvent")).thenReturn(expected);
        final ILightkeeperFramework framework = frameworkWithEvents(events);

        // execute
        final EventCaptureHandle result = framework.captureEvents("org.bukkit.event.player.PlayerJoinEvent");

        // verify
        assertThat(result).isSameAs(expected);
        verify(events).capture("org.bukkit.event.player.PlayerJoinEvent");
    }

    private static ILightkeeperFramework frameworkWithServer(IServerControl server)
    {
        return new FacetStubFramework(server, mock(IWorlds.class), mock(IBots.class), mock(IEvents.class));
    }

    private static ILightkeeperFramework frameworkWithWorlds(IWorlds worlds)
    {
        return new FacetStubFramework(mock(IServerControl.class), worlds, mock(IBots.class), mock(IEvents.class));
    }

    private static ILightkeeperFramework frameworkWithBots(IBots bots)
    {
        return new FacetStubFramework(mock(IServerControl.class), mock(IWorlds.class), bots, mock(IEvents.class));
    }

    private static ILightkeeperFramework frameworkWithEvents(IEvents events)
    {
        return new FacetStubFramework(mock(IServerControl.class), mock(IWorlds.class), mock(IBots.class), events);
    }

    private static WorldHandle worldHandle()
    {
        return FrameworkHandleFactory.worldHandle(mock(IFrameworkGatewayView.class), "w");
    }

    private static PlayerHandle playerHandle()
    {
        return FrameworkHandleFactory.playerHandle(mock(IFrameworkGatewayView.class), UUID.randomUUID(), "bot");
    }

    /**
     * Minimal {@link ILightkeeperFramework} whose facet accessors return the supplied facets and whose deprecated
     * default methods therefore delegate into them.
     */
    private static final class FacetStubFramework implements ILightkeeperFramework
    {
        private final IServerControl server;
        private final IWorlds worlds;
        private final IBots bots;
        private final IEvents events;

        private FacetStubFramework(IServerControl server, IWorlds worlds, IBots bots, IEvents events)
        {
            this.server = Objects.requireNonNull(server);
            this.worlds = Objects.requireNonNull(worlds);
            this.bots = Objects.requireNonNull(bots);
            this.events = Objects.requireNonNull(events);
        }

        @Override
        public IServerControl server()
        {
            return server;
        }

        @Override
        public IWorlds worlds()
        {
            return worlds;
        }

        @Override
        public IBots bots()
        {
            return bots;
        }

        @Override
        public IEvents events()
        {
            return events;
        }

        @Override
        public void waitUntil(Condition condition, Duration timeout)
        {
            throw new UnsupportedOperationException("Not used by the delegate test.");
        }

        @Override
        public void close()
        {
        }
    }
}
