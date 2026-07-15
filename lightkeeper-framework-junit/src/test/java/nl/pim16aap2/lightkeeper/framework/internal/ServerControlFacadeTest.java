package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.Platform;
import nl.pim16aap2.lightkeeper.framework.ServerErrorsHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.GetServerErrors;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerControlFacadeTest
{
    @Test
    void executeCommand_shouldDelegateToAgentClientAndReportSuccess()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        when(agentClient.executeCommand(CommandSource.CONSOLE, "time set day")).thenReturn(true);
        final DefaultLightkeeperFramework framework = framework(agentClient);

        // execute
        final CommandResult result = framework.server().executeCommand(CommandSource.CONSOLE, "time set day");

        // verify
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Command succeeded.");
    }

    @Test
    void executeCommand_shouldReportFailureWhenAgentClientReturnsFalse()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        when(agentClient.executeCommand(CommandSource.CONSOLE, "broken")).thenReturn(false);
        final DefaultLightkeeperFramework framework = framework(agentClient);

        // execute
        final CommandResult result = framework.server().executeCommand(CommandSource.CONSOLE, "broken");

        // verify
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Command failed.");
    }

    @Test
    void output_shouldReturnServerProcessOutputSnapshot()
    {
        // setup
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        when(minecraftServerProcess.snapshotOutputLines()).thenReturn(List.of("line one", "line two"));
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(), minecraftServerProcess, mock(UdsAgentClient.class), new PlayerScopeRegistry());

        // execute
        final List<String> output = framework.server().output();

        // verify
        assertThat(output).containsExactly("line one", "line two");
    }

    @Test
    void platform_shouldReturnValueFromAgentClient()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        when(agentClient.serverPlatform()).thenReturn(Platform.PAPER);
        final DefaultLightkeeperFramework framework = framework(agentClient);

        // execute
        final Platform platform = framework.server().platform();

        // verify
        assertThat(platform).isEqualTo(Platform.PAPER);
    }

    @Test
    void errors_shouldReturnHandleBackedByAgentClient()
    {
        // setup — the handle must delegate back through the framework that created it
        final MinecraftServerProcess minecraftServerProcess = mock(MinecraftServerProcess.class);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        when(agentClient.getServerErrors()).thenReturn(new GetServerErrors.Response(List.of(), 0L, true));
        when(minecraftServerProcess.snapshotStderrLinesFrom(0L)).thenReturn(List.of());
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(), minecraftServerProcess, agentClient, new PlayerScopeRegistry());

        // execute
        final ServerErrorsHandle handle = framework.server().errors();

        // verify
        assertThat(handle.getCaptured()).isEmpty();
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
