package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.EventCaptureHandle;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventsFacadeTest
{
    @Test
    void capture_shouldRegisterListenerAndReturnHandle()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final DefaultLightkeeperFramework framework = framework(agentClient);

        // execute
        final EventCaptureHandle handle =
            framework.events().capture("org.bukkit.event.player.PlayerJoinEvent");

        // verify
        assertThat(handle).isNotNull();
        verify(agentClient).registerEventListener("org.bukkit.event.player.PlayerJoinEvent");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void capture_shouldThrowNullPointerExceptionWhenEventClassNameIsNull()
    {
        // setup
        final DefaultLightkeeperFramework framework = framework(mock(UdsAgentClient.class));

        // execute + verify
        assertThatThrownBy(() -> framework.events().capture(null))
            .isInstanceOf(NullPointerException.class);
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
