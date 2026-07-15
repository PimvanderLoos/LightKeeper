package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.IWorldBuilder;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldsFacadeTest
{
    @Test
    void main_shouldReturnHandleForAgentMainWorld()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        when(agentClient.mainWorld()).thenReturn("world");
        final DefaultLightkeeperFramework framework = framework(agentClient);

        // execute
        final WorldHandle result = framework.worlds().main();

        // verify
        assertThat(result.name()).isEqualTo("world");
    }

    @Test
    void create_shouldCreateWorldWithFrameworkDefaults()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        when(agentClient.newWorld(any(WorldSpec.class))).thenReturn("created");
        final DefaultLightkeeperFramework framework = framework(agentClient);

        // execute
        final WorldHandle result = framework.worlds().create();

        // verify
        assertThat(result.name()).isEqualTo("created");
        final ArgumentCaptor<WorldSpec> specCaptor = ArgumentCaptor.forClass(WorldSpec.class);
        verify(agentClient).newWorld(specCaptor.capture());
        assertThat(specCaptor.getValue().worldType()).isEqualTo(WorldSpec.WorldType.NORMAL);
        assertThat(specCaptor.getValue().environment()).isEqualTo(WorldSpec.WorldEnvironment.NORMAL);
        assertThat(specCaptor.getValue().seed()).isEqualTo(0L);
        assertThat(specCaptor.getValue().name()).isNotBlank();
    }

    @Test
    void create_shouldValidateAndTrimWorldSpecName()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        when(agentClient.newWorld(new WorldSpec(
            "myworld", WorldSpec.WorldType.FLAT, WorldSpec.WorldEnvironment.NETHER, 5L)))
            .thenReturn("myworld-instance");
        final DefaultLightkeeperFramework framework = framework(agentClient);

        // execute
        final WorldHandle result = framework.worlds().create(new WorldSpec(
            "  myworld  ", WorldSpec.WorldType.FLAT, WorldSpec.WorldEnvironment.NETHER, 5L));

        // verify
        assertThat(result.name()).isEqualTo("myworld-instance");
        verify(agentClient).newWorld(new WorldSpec(
            "myworld", WorldSpec.WorldType.FLAT, WorldSpec.WorldEnvironment.NETHER, 5L));
    }

    @Test
    void create_shouldThrowExceptionWhenWorldSpecNameIsBlank()
    {
        // setup
        final DefaultLightkeeperFramework framework = framework(mock(UdsAgentClient.class));

        // execute + verify
        assertThatThrownBy(() -> framework.worlds().create(new WorldSpec(
            "   ", WorldSpec.WorldType.NORMAL, WorldSpec.WorldEnvironment.NORMAL, 0L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void builder_shouldReturnWorldBuilder()
    {
        // setup
        final DefaultLightkeeperFramework framework = framework(mock(UdsAgentClient.class));

        // execute
        final IWorldBuilder builder = framework.worlds().builder();

        // verify
        assertThat(builder).isNotNull();
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void create_shouldThrowNullPointerExceptionWhenWorldSpecIsNull()
    {
        // setup
        final DefaultLightkeeperFramework framework = framework(mock(UdsAgentClient.class));

        // execute + verify
        assertThatThrownBy(() -> framework.worlds().create(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("worldSpec");
    }

    private static DefaultLightkeeperFramework framework(UdsAgentClient agentClient)
    {
        return new DefaultLightkeeperFramework(
            runtimeManifest(), mock(MinecraftServerProcess.class), agentClient, new PlayerScopeRegistry());
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
