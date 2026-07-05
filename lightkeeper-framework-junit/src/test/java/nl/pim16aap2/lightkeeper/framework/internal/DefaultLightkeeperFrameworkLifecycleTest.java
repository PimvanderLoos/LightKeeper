package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
            List.of()
        );
    }
}
