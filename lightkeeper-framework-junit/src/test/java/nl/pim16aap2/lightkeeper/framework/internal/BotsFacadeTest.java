package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IPlayerBuilder;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotsFacadeTest
{
    @Test
    void join_shouldCreatePlayerRegisterItAndReturnHandle()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);
        final UUID uuid = UUID.randomUUID();
        when(agentClient.createPlayer("lkbot001", uuid, "world", null, null, null, null, null))
            .thenReturn(new AgentPlayerData(uuid, "lkbot001"));
        final DefaultLightkeeperFramework framework = framework(agentClient, playerScopeRegistry);
        final WorldHandle world = FrameworkHandleFactory.worldHandle(framework, "world");

        // execute
        final PlayerHandle result = framework.bots().join("lkbot001", uuid, world);

        // verify
        assertThat(result.name()).isEqualTo("lkbot001");
        assertThat(result.uniqueId()).isEqualTo(uuid);
        verify(playerScopeRegistry).register(uuid);
    }

    @Test
    void join_shouldDefaultToRandomUuidWhenNotProvided()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final PlayerScopeRegistry playerScopeRegistry = mock(PlayerScopeRegistry.class);
        final UUID generatedUuid = UUID.randomUUID();
        when(agentClient.createPlayer(
            eq("lkbot002"), any(UUID.class), eq("world"), isNull(), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(new AgentPlayerData(generatedUuid, "lkbot002"));
        final DefaultLightkeeperFramework framework = framework(agentClient, playerScopeRegistry);
        final WorldHandle world = FrameworkHandleFactory.worldHandle(framework, "world");

        // execute
        final PlayerHandle result = framework.bots().join("lkbot002", world);

        // verify
        assertThat(result.name()).isEqualTo("lkbot002");
        verify(playerScopeRegistry).register(generatedUuid);
    }

    @Test
    void join_shouldThrowExceptionWhenNameIsBlank()
    {
        // setup
        final DefaultLightkeeperFramework framework =
            framework(mock(UdsAgentClient.class), new PlayerScopeRegistry());
        final WorldHandle world = FrameworkHandleFactory.worldHandle(framework, "world");

        // execute + verify
        assertThatThrownBy(() -> framework.bots().join("   ", UUID.randomUUID(), world))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void join_shouldThrowExceptionWhenNameExceedsSixteenCharacters()
    {
        // setup
        final DefaultLightkeeperFramework framework =
            framework(mock(UdsAgentClient.class), new PlayerScopeRegistry());
        final WorldHandle world = FrameworkHandleFactory.worldHandle(framework, "world");

        // execute + verify
        assertThatThrownBy(() -> framework.bots().join("this_name_is_way_too_long", UUID.randomUUID(), world))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("16");
    }

    @Test
    void builder_shouldReturnPlayerBuilder()
    {
        // setup
        final DefaultLightkeeperFramework framework =
            framework(mock(UdsAgentClient.class), new PlayerScopeRegistry());

        // execute
        final IPlayerBuilder builder = framework.bots().builder();

        // verify
        assertThat(builder).isNotNull();
    }

    private static DefaultLightkeeperFramework framework(
        UdsAgentClient agentClient, PlayerScopeRegistry playerScopeRegistry)
    {
        return new DefaultLightkeeperFramework(
            runtimeManifest(), mock(MinecraftServerProcess.class), agentClient, playerScopeRegistry);
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
