package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.DropResult;
import nl.pim16aap2.lightkeeper.protocol.MutatePlayerPermission;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLightkeeperFrameworkGatewayTest
{
    @Test
    void grantPermission_shouldDelegateToAgentClientWithGrantMode()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );
        final UUID playerId = UUID.randomUUID();

        // execute
        framework.grantPermission(playerId, "lightkeeper.fly");

        // verify
        verify(agentClient).mutatePlayerPermission(playerId, "lightkeeper.fly", MutatePlayerPermission.Mode.GRANT);
    }

    @Test
    void revokePermission_shouldDelegateToAgentClientWithRevokeMode()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );
        final UUID playerId = UUID.randomUUID();

        // execute
        framework.revokePermission(playerId, "lightkeeper.fly");

        // verify
        verify(agentClient).mutatePlayerPermission(playerId, "lightkeeper.fly", MutatePlayerPermission.Mode.REVOKE);
    }

    @Test
    void unsetPermission_shouldDelegateToAgentClientWithUnsetMode()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );
        final UUID playerId = UUID.randomUUID();

        // execute
        framework.unsetPermission(playerId, "lightkeeper.fly");

        // verify
        verify(agentClient).mutatePlayerPermission(playerId, "lightkeeper.fly", MutatePlayerPermission.Mode.UNSET);
    }

    @Test
    void grantPermission_shouldThrowExceptionWhenPermissionIsBlank()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.grantPermission(UUID.randomUUID(), "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void grantPermission_shouldThrowNullPointerExceptionWhenPlayerIdIsNull()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.grantPermission(null, "lightkeeper.fly"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void hasPermission_shouldReturnValueFromAgentClient()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        when(agentClient.hasPlayerPermission(playerId, "lightkeeper.fly")).thenReturn(true);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final boolean result = framework.hasPermission(playerId, "lightkeeper.fly");

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void serverDirectory_shouldReturnPathFromManifest()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute
        final Path serverDirectory = framework.serverDirectory();

        // verify
        assertThat(serverDirectory).isEqualTo(Path.of("/tmp/lightkeeper/server"));
    }

    @Test
    void pluginDataDirectory_shouldResolvePluginsSubdirectory()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute
        final Path pluginDataDirectory = framework.pluginDataDirectory("AnimatedArchitecture");

        // verify
        assertThat(pluginDataDirectory)
            .isEqualTo(Path.of("/tmp/lightkeeper/server", "plugins", "AnimatedArchitecture"));
    }

    @Test
    void pluginDataDirectory_shouldThrowExceptionWhenNameIsBlank()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.pluginDataDirectory("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void pluginDataDirectory_shouldThrowExceptionWhenNameContainsParentTraversal()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.pluginDataDirectory("../evil"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pluginDataDirectory_shouldThrowExceptionWhenNameContainsSlash()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.pluginDataDirectory("a/b"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newWorldFromTemplate_shouldCreateWorldFromProvisionedTemplate()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        when(agentClient.newWorld(new WorldSpec(
            "template-a", WorldSpec.WorldType.FLAT, WorldSpec.WorldEnvironment.NETHER, 7L)))
            .thenReturn("template-a-instance");
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifestWithProvisionedWorlds(List.of(
                new RuntimeManifest.ProvisionedWorld("template-a", "NETHER", "FLAT", 7L, false)
            )),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final WorldHandle result = framework.newWorldFromTemplate("template-a");

        // verify
        assertThat(result.name()).isEqualTo("template-a-instance");
        verify(agentClient).newWorld(new WorldSpec(
            "template-a", WorldSpec.WorldType.FLAT, WorldSpec.WorldEnvironment.NETHER, 7L));
    }

    @Test
    void newWorldFromTemplate_shouldThrowExceptionWhenTemplateIsNotProvisioned()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifestWithProvisionedWorlds(List.of(
                new RuntimeManifest.ProvisionedWorld("template-a", "NORMAL", "NORMAL", 0L, false),
                new RuntimeManifest.ProvisionedWorld("template-b", "NORMAL", "NORMAL", 0L, false)
            )),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.newWorldFromTemplate("template-x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("template-a")
            .hasMessageContaining("template-b");
    }

    @Test
    void newWorldFromTemplate_shouldThrowExceptionWhenTemplateNameIsBlank()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.newWorldFromTemplate("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void newWorldFromTemplate_shouldThrowNullPointerExceptionWhenTemplateNameIsNull()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.newWorldFromTemplate(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void leftClickBlock_shouldReturnTrueWhenAgentClientReportsCancelled()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        final Vector3Di position = new Vector3Di(1, 64, 1);
        when(agentClient.leftClickBlock(playerId, position, "UP")).thenReturn(true);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final boolean result = framework.leftClickBlock(playerId, position, "UP");

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void leftClickBlock_shouldReturnFalseWhenAgentClientReportsNotCancelled()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        final Vector3Di position = new Vector3Di(1, 64, 1);
        when(agentClient.leftClickBlock(playerId, position, "UP")).thenReturn(false);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final boolean result = framework.leftClickBlock(playerId, position, "UP");

        // verify
        assertThat(result).isFalse();
    }

    @Test
    void rightClickBlock_shouldReturnTrueWhenAgentClientReportsCancelled()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        final Vector3Di position = new Vector3Di(1, 64, 1);
        when(agentClient.rightClickBlock(playerId, position, "UP")).thenReturn(true);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final boolean result = framework.rightClickBlock(playerId, position, "UP");

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void rightClickBlock_shouldReturnFalseWhenAgentClientReportsNotCancelled()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        final Vector3Di position = new Vector3Di(1, 64, 1);
        when(agentClient.rightClickBlock(playerId, position, "UP")).thenReturn(false);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final boolean result = framework.rightClickBlock(playerId, position, "UP");

        // verify
        assertThat(result).isFalse();
    }

    @Test
    void dropItem_shouldReturnDropResultFromAgentClient()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        when(agentClient.dropItem(playerId)).thenReturn(DropResult.EMPTY_HAND);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final DropResult result = framework.dropItem(playerId);

        // verify
        assertThat(result).isEqualTo(DropResult.EMPTY_HAND);
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

    private static RuntimeManifest runtimeManifestWithProvisionedWorlds(
        List<RuntimeManifest.ProvisionedWorld> provisionedWorlds)
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
            provisionedWorlds
        );
    }
}
