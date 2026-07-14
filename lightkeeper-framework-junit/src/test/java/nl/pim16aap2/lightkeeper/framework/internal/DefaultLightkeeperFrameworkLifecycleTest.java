package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLightkeeperFrameworkLifecycleTest
{
    @Test
    void close_shouldCleanupRegisteredPlayersAndStopServerProcess()
    {
        // setup
        final RuntimeManifest runtimeManifest = runtimeManifest();
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = new PlayerScopeRegistry();
        final UUID playerId = UUID.randomUUID();
        playerScopeRegistry.register(playerId);

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest,
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );

        // execute
        framework.close();

        // verify
        verify(agentClient, times(1)).removePlayer(playerId);
        verify(agentClient, times(1)).close();
        verify(minecraftServerProcess, times(1)).stop(java.time.Duration.ofSeconds(45));
    }

    @Test
    void ensureOpen_shouldThrowExceptionAfterFrameworkIsClosed()
    {
        // setup
        final RuntimeManifest runtimeManifest = runtimeManifest();
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest,
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );
        framework.close();

        // execute + verify
        assertThatThrownBy(framework::ensureOpen)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already closed");
    }

    @Test
    void serverOutput_shouldReturnServerProcessOutputSnapshot()
    {
        // setup
        final RuntimeManifest runtimeManifest = runtimeManifest();
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.snapshotOutputLines()).thenReturn(List.of("line one", "line two"));
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest,
            minecraftServerProcess,
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute
        final List<String> serverOutput = framework.serverOutput();

        // verify
        assertThat(serverOutput).containsExactly("line one", "line two");
    }

    @Test
    void crashServer_shouldInvalidatePlayersAndKillProcess()
    {
        // setup
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );

        // execute
        framework.crashServer();

        // verify
        verify(playerScopeRegistry, times(1)).invalidateAll();
        verify(agentClient, times(1)).close();
        verify(minecraftServerProcess, times(1)).kill();
    }

    @Test
    void stopServer_shouldCleanupPlayersCloseClientAndStopProcessInOrder()
    {
        // setup
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.isRunning()).thenReturn(true);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );

        // execute
        framework.stopServer();

        // verify
        final InOrder inOrder = inOrder(playerScopeRegistry, agentClient, minecraftServerProcess);
        inOrder.verify(playerScopeRegistry, times(1)).cleanupAll(any());
        inOrder.verify(agentClient, times(1)).close();
        inOrder.verify(minecraftServerProcess, times(1)).stop(java.time.Duration.ofSeconds(45));
    }

    @Test
    void stopServer_shouldThrowExceptionAndNotInteractWhenProcessIsNotRunning()
    {
        // setup
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.isRunning()).thenReturn(false);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );

        // execute + verify
        assertThatThrownBy(framework::stopServer)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not running");
        verify(playerScopeRegistry, never()).cleanupAll(any());
        verify(agentClient, never()).close();
        verify(minecraftServerProcess, never()).stop(any());
    }

    @Test
    void stopServer_shouldStillStopProcessAndPropagateWhenCleanupThrows()
    {
        // Note: the real PlayerScopeRegistry.cleanupAll swallows per-player failures and cannot currently throw;
        // this test pins the structural try/finally contract (process stop must survive a cleanup failure).
        // setup
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.isRunning()).thenReturn(true);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);
        final RuntimeException cleanupFailure = new RuntimeException("cleanup boom");
        doThrow(cleanupFailure).when(playerScopeRegistry).cleanupAll(any());

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );

        // execute + verify: the client close is skipped (mirroring close()), but the process is still stopped
        assertThatThrownBy(framework::stopServer).isSameAs(cleanupFailure);
        verify(agentClient, never()).close();
        verify(minecraftServerProcess, times(1)).stop(java.time.Duration.ofSeconds(45));
    }

    @Test
    void startServer_shouldThrowExceptionWhenProcessIsRunning()
    {
        // setup
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.isRunning()).thenReturn(true);

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            minecraftServerProcess,
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(framework::startServer)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already running");
    }

    @Test
    void startServer_shouldStartProcessRehandshakePreloadWorldsAndAllowMethodScopeAfterServerWasDown()
    {
        // setup
        final RuntimeManifest runtimeManifest = runtimeManifestWithPreloadedWorld();
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.isRunning()).thenReturn(false);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest,
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );
        framework.crashServer();

        // execute
        framework.startServer();

        // verify
        verify(minecraftServerProcess, times(1)).start(java.time.Duration.ofMinutes(2));
        verify(agentClient, times(1)).rehandshake(
            java.time.Duration.ofSeconds(45),
            runtimeManifest.agentAuthToken(),
            runtimeManifest.runtimeProtocolVersion(),
            java.util.Objects.requireNonNull(runtimeManifest.agentJarSha256())
        );
        verify(agentClient, times(1)).newWorld(new WorldSpec(
            "preload_world",
            WorldSpec.WorldType.NORMAL,
            WorldSpec.WorldEnvironment.NORMAL,
            42L
        ));

        framework.beginMethodScope("method-after-restart");
        verify(playerScopeRegistry, times(1)).beginMethodScope("method-after-restart");
    }

    @Test
    void startServer_shouldStopProcessAndRethrowWhenRehandshakeFails()
    {
        // setup
        final RuntimeManifest runtimeManifest = runtimeManifest();
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.isRunning()).thenReturn(false);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);
        final RuntimeException rehandshakeFailure = new RuntimeException("handshake boom");
        doThrow(rehandshakeFailure).when(agentClient).rehandshake(any(), any(), anyInt(), any());

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest,
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );
        framework.crashServer();

        // execute + verify: the failure propagates and the process is stopped again so a retry stays possible
        assertThatThrownBy(framework::startServer).isSameAs(rehandshakeFailure);
        verify(minecraftServerProcess, times(1)).start(java.time.Duration.ofMinutes(2));
        verify(minecraftServerProcess, times(1)).stop(java.time.Duration.ofSeconds(45));
        assertThatThrownBy(() -> framework.beginMethodScope("method-after-failed-start"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restartServer_shouldStartProcessAndRehandshake()
    {
        // setup
        final RuntimeManifest runtimeManifest = runtimeManifest();
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.isRunning()).thenReturn(false);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest,
            minecraftServerProcess,
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        framework.restartServer();

        // verify
        verify(minecraftServerProcess, times(1)).start(java.time.Duration.ofMinutes(2));
        verify(agentClient, times(1)).rehandshake(
            java.time.Duration.ofSeconds(45),
            runtimeManifest.agentAuthToken(),
            runtimeManifest.runtimeProtocolVersion(),
            java.util.Objects.requireNonNull(runtimeManifest.agentJarSha256())
        );
    }

    @Test
    void restartServer_shouldStopThenStartWhenProcessIsRunning()
    {
        // setup
        final RuntimeManifest runtimeManifest = runtimeManifest();
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        // Consecutive stubbing models the process actually stopping mid-call: running for the restart check,
        // then down for the subsequent start check (the internal stop path performs no check of its own).
        when(minecraftServerProcess.isRunning()).thenReturn(true, false);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest,
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );

        // execute
        framework.restartServer();

        // verify
        final InOrder inOrder = inOrder(minecraftServerProcess);
        inOrder.verify(minecraftServerProcess, times(1)).stop(java.time.Duration.ofSeconds(45));
        inOrder.verify(minecraftServerProcess, times(1)).start(java.time.Duration.ofMinutes(2));
    }

    @Test
    void restartServer_shouldSkipStopWhenServerIsAlreadyDown()
    {
        // setup
        final RuntimeManifest runtimeManifest = runtimeManifest();
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.isRunning()).thenReturn(false);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest,
            minecraftServerProcess,
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        framework.restartServer();

        // verify
        verify(minecraftServerProcess, never()).stop(any());
        verify(agentClient, never()).close();
        verify(minecraftServerProcess, times(1)).start(java.time.Duration.ofMinutes(2));
    }

    @Test
    void beginMethodScope_shouldThrowAfterCrashWithoutRestart()
    {
        // setup
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );
        framework.crashServer();

        // execute + verify
        assertThatThrownBy(() -> framework.beginMethodScope("method-2"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("crashServer")
            .hasMessageContaining("restartServer");
        verify(playerScopeRegistry, never()).beginMethodScope("method-2");
    }

    @Test
    void beginMethodScope_shouldSucceedAfterCrashAndRestart()
    {
        // setup
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.isRunning()).thenReturn(false);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );
        framework.crashServer();
        framework.restartServer();

        // execute
        framework.beginMethodScope("method-2");

        // verify
        verify(playerScopeRegistry, times(1)).beginMethodScope("method-2");
    }

    @Test
    void beginMethodScope_shouldThrowAfterStopWithoutStart()
    {
        // setup
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.isRunning()).thenReturn(true);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            minecraftServerProcess,
            agentClient,
            playerScopeRegistry
        );
        framework.stopServer();

        // execute + verify
        assertThatThrownBy(() -> framework.beginMethodScope("method-3"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("stopServer");
        verify(playerScopeRegistry, never()).beginMethodScope("method-3");
    }

    private static RuntimeManifest runtimeManifest()
    {
        return new RuntimeManifest(
            "paper",
            "1.21.11",
            1L,
            "cache-key",
            "/tmp/lightkeeper/server",
            "/tmp/lightkeeper/server/server.jar",
            1024,
            "/tmp/lightkeeper/agent.sock",
            "auth-token",
            "/tmp/lightkeeper/agent.jar",
            "agent-sha256",
            1,
            "agent-cache-identity",
            null,
            List.of(),
            List.of()
        );
    }

    private static RuntimeManifest runtimeManifestWithPreloadedWorld()
    {
        return new RuntimeManifest(
            "paper",
            "1.21.11",
            1L,
            "cache-key",
            "/tmp/lightkeeper/server",
            "/tmp/lightkeeper/server/server.jar",
            1024,
            "/tmp/lightkeeper/agent.sock",
            "auth-token",
            "/tmp/lightkeeper/agent.jar",
            "agent-sha256",
            1,
            "agent-cache-identity",
            null,
            List.of(new RuntimeManifest.PreloadedWorld("preload_world", "NORMAL", "NORMAL", 42L)),
            List.of("preload_world")
        );
    }
}
