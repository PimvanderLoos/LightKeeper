package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.provisioning.session.ServerSession;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestWriter;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultLightkeeperFrameworkSessionTest
{
    @Mock
    private MinecraftServerProcess serverProcess;
    @Mock
    private UdsAgentClient agentClient;
    @Mock
    private ServerSession session;

    @AfterEach
    void verifyInteractions()
    {
        verifyNoMoreInteractions(serverProcess, agentClient, session);
    }

    @Test
    void close_shouldStopTheServerBeforeCleaningItsOwnedSession(@TempDir Path root)
        throws Exception
    {
        // setup
        final DefaultLightkeeperFramework framework = framework(root);

        // execute
        framework.close();
        framework.close();

        // verify
        final var order = inOrder(agentClient, serverProcess, session);
        order.verify(agentClient).close();
        order.verify(serverProcess).stop(DefaultLightkeeperFramework.SHUTDOWN_TIMEOUT);
        order.verify(session).close();
    }

    @Test
    void close_shouldRetainSessionWhenTheServerCannotBeStopped(@TempDir Path root)
        throws Exception
    {
        // setup
        final DefaultLightkeeperFramework framework = framework(root);
        doThrow(new IllegalStateException("stop failed"))
            .when(serverProcess).stop(DefaultLightkeeperFramework.SHUTDOWN_TIMEOUT);

        // execute
        final var failure = catchThrowable(framework::close);

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessage("stop failed");
        final var order = inOrder(agentClient, serverProcess, session);
        order.verify(agentClient).close();
        order.verify(serverProcess).stop(DefaultLightkeeperFramework.SHUTDOWN_TIMEOUT);
        order.verify(session).preserve();
        order.verify(session).close();
    }

    @Test
    void close_shouldKeepTheFirstFailureAndSuppressLaterShutdownFailures(@TempDir Path root)
        throws Exception
    {
        // setup
        final DefaultLightkeeperFramework framework = framework(root);
        final var firstFailure = new IllegalStateException("agent close failed");
        final var stopFailure = new IllegalStateException("stop failed");
        final var cleanupFailure = new IllegalStateException("session cleanup failed");
        doThrow(firstFailure).when(agentClient).close();
        doThrow(stopFailure).when(serverProcess).stop(DefaultLightkeeperFramework.SHUTDOWN_TIMEOUT);
        doThrow(cleanupFailure).when(session).close();

        // execute
        final var failure = catchThrowable(framework::close);

        // verify
        assertThat(failure).isSameAs(firstFailure);
        assertThat(failure.getSuppressed()).containsExactly(stopFailure, cleanupFailure);
        final var order = inOrder(agentClient, serverProcess, session);
        order.verify(agentClient).close();
        order.verify(serverProcess).stop(DefaultLightkeeperFramework.SHUTDOWN_TIMEOUT);
        order.verify(session).preserve();
        order.verify(session).close();
    }

    @Test
    void close_shouldAvoidSelfSuppressionWhenShutdownReusesAnException(@TempDir Path root)
        throws Exception
    {
        // setup
        final DefaultLightkeeperFramework framework = framework(root);
        final var sharedFailure = new IllegalStateException("shared close failure");
        doThrow(sharedFailure).when(agentClient).close();
        doThrow(sharedFailure).when(serverProcess).stop(DefaultLightkeeperFramework.SHUTDOWN_TIMEOUT);

        // execute
        final var failure = catchThrowable(framework::close);

        // verify
        assertThat(failure).isSameAs(sharedFailure);
        assertThat(failure.getSuppressed()).isEmpty();
        final var order = inOrder(agentClient, serverProcess, session);
        order.verify(agentClient).close();
        order.verify(serverProcess).stop(DefaultLightkeeperFramework.SHUTDOWN_TIMEOUT);
        order.verify(session).preserve();
        order.verify(session).close();
    }

    @Test
    void start_shouldRetainFailedSessionWhenManifestValidationFails(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path manifestPath = root.resolve("runtime-manifest.json");
        new RuntimeManifestWriter().write(runtimeManifest(root), manifestPath);
        when(session.manifestPath()).thenReturn(manifestPath);

        // execute
        final var failure = catchThrowable(() -> DefaultLightkeeperFramework.start(session));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("Server directory");
        verify(session).manifestPath();
        verify(session).preserve();
        verify(session).close();
    }

    private DefaultLightkeeperFramework framework(Path root)
        throws ReflectiveOperationException
    {
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(root),
            serverProcess,
            agentClient,
            new PlayerScopeRegistry()
        );
        final Field field = DefaultLightkeeperFramework.class.getDeclaredField("ownedSession");
        field.setAccessible(true);
        field.set(framework, session);
        return framework;
    }

    private static RuntimeManifest runtimeManifest(Path root)
    {
        return new RuntimeManifest(
            "paper", "1.21.11", 132, "cache", root.resolve("missing-server").toString(),
            root.resolve("missing-server/paper.jar").toString(), 1024, root.resolve("socket/lk.sock").toString(),
            "token", root.resolve("missing-server/plugins/agent.jar").toString(), null, RuntimeProtocol.VERSION,
            "agent-cache", null, List.of()
        );
    }
}
