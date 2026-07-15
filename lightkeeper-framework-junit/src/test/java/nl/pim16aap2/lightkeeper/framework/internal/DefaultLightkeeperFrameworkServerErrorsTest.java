package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import nl.pim16aap2.lightkeeper.framework.ServerErrorsHandle;
import nl.pim16aap2.lightkeeper.protocol.GetServerErrors;
import nl.pim16aap2.lightkeeper.protocol.ServerErrorEntry;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link DefaultLightkeeperFramework}'s structured server-error surface: mapping agent-side entries to
 * {@link ServerErrorSnapshot}s, appending raw stderr detections, and the stderr watermark advanced by
 * {@link DefaultLightkeeperFramework#clearServerErrors()}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DefaultLightkeeperFrameworkServerErrorsTest
{
    @Mock
    private MinecraftServerProcess minecraftServerProcess;
    @Mock
    private UdsAgentClient agentClient;

    private DefaultLightkeeperFramework framework()
    {
        return new DefaultLightkeeperFramework(
            runtimeManifest(), minecraftServerProcess, agentClient, new PlayerScopeRegistry());
    }

    @Test
    void serverErrors_shouldReturnHandleBoundToFramework()
    {
        // setup — the handle must delegate back through the very framework that created it
        final DefaultLightkeeperFramework framework = framework();
        when(agentClient.getServerErrors()).thenReturn(new GetServerErrors.Response(List.of(), 0L, true));
        when(minecraftServerProcess.snapshotStderrLinesFrom(0L)).thenReturn(List.of());

        // execute
        final ServerErrorsHandle handle = framework.server().errors();

        // verify
        assertThat(handle.getCaptured()).isEmpty();
    }

    @Test
    void capturedServerErrors_shouldMapEntriesAndAppendStderrDetections()
    {
        // setup
        final DefaultLightkeeperFramework framework = framework();
        final ServerErrorEntry entry = new ServerErrorEntry(
            10L, "ERROR", "ERROR", "net.example.SomePlugin", "Server thread", "boom",
            "java.lang.IllegalStateException", "boom", List.of("java.lang.IllegalStateException: boom"));
        when(agentClient.getServerErrors()).thenReturn(new GetServerErrors.Response(List.of(entry), 0L, true));
        when(minecraftServerProcess.snapshotStderrLinesFrom(0L)).thenReturn(List.of(
            new MinecraftServerProcess.OutputLine(
                "java.lang.RuntimeException: raw", true, 20L)));

        // execute
        final List<ServerErrorSnapshot> snapshots = framework.capturedServerErrors();

        // verify — structured entries come first, then raw stderr detections
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).message()).isEqualTo("boom");
        assertThat(snapshots.get(0).severity()).isEqualTo(ServerErrorSnapshot.Severity.ERROR);
        assertThat(snapshots.get(1).message()).isEqualTo("java.lang.RuntimeException: raw");
        assertThat(snapshots.get(1).loggerName()).isEqualTo(ServerErrorSnapshot.LOGGER_NAME_STDERR);
    }

    @Test
    void capturedServerErrors_shouldMapWarningSeverityForNonErrorEntries()
    {
        // setup
        final DefaultLightkeeperFramework framework = framework();
        final ServerErrorEntry entry = new ServerErrorEntry(
            10L, "WARNING", "WARN", "net.example.SomePlugin", "Server thread", "careful", null, null, List.of());
        when(agentClient.getServerErrors()).thenReturn(new GetServerErrors.Response(List.of(entry), 0L, true));
        when(minecraftServerProcess.snapshotStderrLinesFrom(0L)).thenReturn(List.of());

        // execute
        final List<ServerErrorSnapshot> snapshots = framework.capturedServerErrors();

        // verify
        assertThat(snapshots).singleElement()
            .extracting(ServerErrorSnapshot::severity)
            .isEqualTo(ServerErrorSnapshot.Severity.WARNING);
    }

    @Test
    void capturedServerErrors_shouldThrowWhenCaptureInactive()
    {
        // setup
        final DefaultLightkeeperFramework framework = framework();
        when(agentClient.getServerErrors()).thenReturn(new GetServerErrors.Response(List.of(), 0L, false));

        // execute + verify
        assertThatThrownBy(framework::capturedServerErrors)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Structured server-error capture is inactive");
    }

    @Test
    void capturedServerErrors_shouldNotThrowWhenEntriesWereDropped()
    {
        // setup — a full buffer must not fail the call, only be reported
        final DefaultLightkeeperFramework framework = framework();
        when(agentClient.getServerErrors()).thenReturn(new GetServerErrors.Response(List.of(), 7L, true));
        when(minecraftServerProcess.snapshotStderrLinesFrom(0L)).thenReturn(List.of());

        // execute
        final List<ServerErrorSnapshot> snapshots = framework.capturedServerErrors();

        // verify
        assertThat(snapshots).isEmpty();
    }

    @Test
    void clearServerErrors_shouldSnapshotWatermarkBeforeSendingClearRpc()
    {
        // setup — the watermark must be taken before the RPC so stderr lines written during the round trip
        // stay above it and still surface in later capturedServerErrors() calls
        final DefaultLightkeeperFramework framework = framework();
        when(minecraftServerProcess.totalOutputLineCount()).thenReturn(42L);

        // execute
        framework.clearServerErrors();

        // verify
        final InOrder callOrder = inOrder(minecraftServerProcess, agentClient);
        callOrder.verify(minecraftServerProcess).totalOutputLineCount();
        callOrder.verify(agentClient).clearServerErrors();
    }

    @Test
    void capturedServerErrors_shouldUseWatermarkAdvancedByPriorClear()
    {
        // setup
        final DefaultLightkeeperFramework framework = framework();
        when(minecraftServerProcess.totalOutputLineCount()).thenReturn(42L);
        framework.clearServerErrors();
        when(agentClient.getServerErrors()).thenReturn(new GetServerErrors.Response(List.of(), 0L, true));
        when(minecraftServerProcess.snapshotStderrLinesFrom(42L)).thenReturn(List.of());

        // execute
        framework.capturedServerErrors();

        // verify — the scan window starts at the watermark set by the earlier clear, not at zero
        verify(minecraftServerProcess).snapshotStderrLinesFrom(eq(42L));
    }

    @Test
    void endMethodScope_shouldClearServerErrorsWhenNotCrashed()
    {
        // setup
        final DefaultLightkeeperFramework framework = framework();
        when(minecraftServerProcess.totalOutputLineCount()).thenReturn(5L);
        framework.beginMethodScope("method-1");

        // execute
        framework.endMethodScope("method-1");

        // verify
        verify(agentClient).clearServerErrors();
    }

    @Test
    void endMethodScope_shouldNotClearServerErrorsAfterCrash()
    {
        // setup
        final DefaultLightkeeperFramework framework = framework();
        framework.beginMethodScope("method-1");
        framework.server().crash();

        // execute
        framework.endMethodScope("method-1");

        // verify — the agent connection is gone after a crash; clearing must be skipped
        verify(agentClient, never()).clearServerErrors();
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
