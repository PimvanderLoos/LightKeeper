package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscovery;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeLock;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeSelection;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestWriter;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultLightkeeperFrameworkIdeRuntimeTest
{
    @Test
    void close_shouldReleaseIdeLockAfterNormalShutdown(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final RuntimeManifest manifest = runtimeManifest(tempDirectory);
        final MinecraftServerProcess serverProcess = mock(MinecraftServerProcess.class);
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            manifest,
            serverProcess,
            agentClient,
            new PlayerScopeRegistry()
        );
        final IdeRuntimeLock lock = IdeRuntimeLock.acquire(tempDirectory);
        setRuntimeLock(framework, lock);

        // execute
        framework.close();

        // verify
        verify(agentClient).close();
        verify(serverProcess).stop(DefaultLightkeeperFramework.SHUTDOWN_TIMEOUT);
        assertThatCode(() -> IdeRuntimeLock.acquire(tempDirectory).close()).doesNotThrowAnyException();
    }

    @Test
    void start_shouldReleaseIdeLockWhenValidationFails(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path manifestPath = tempDirectory.resolve("runtime-manifest.json");
        final RuntimeManifest manifest = runtimeManifest(tempDirectory);
        new RuntimeManifestWriter().write(manifest, manifestPath);
        final IdeRuntimeDiscovery discovery = new IdeRuntimeDiscovery(
            IdeRuntimeDiscovery.SCHEMA_VERSION,
            tempDirectory.toRealPath().toString(),
            "prepare-paper",
            "paper",
            manifestPath.toString(),
            "fingerprint"
        );
        final IdeRuntimeSelection selection = new IdeRuntimeSelection(
            tempDirectory,
            manifestPath,
            discovery
        );
        final IdeRuntimeLock lock = IdeRuntimeLock.acquire(tempDirectory);

        // execute + verify
        assertThatThrownBy(() -> DefaultLightkeeperFramework.start(selection, lock))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Server directory")
            .hasMessageContaining("does not exist");
        assertThatCode(() -> IdeRuntimeLock.acquire(tempDirectory).close()).doesNotThrowAnyException();
        assertThat(Files.exists(manifestPath)).isTrue();
    }

    private static RuntimeManifest runtimeManifest(Path tempDirectory)
    {
        return new RuntimeManifest(
            "paper",
            "1.21.11",
            116L,
            "cache",
            tempDirectory.resolve("missing-server").toString(),
            tempDirectory.resolve("missing-server/paper.jar").toString(),
            1024,
            tempDirectory.resolve("socket/lk.sock").toString(),
            "token",
            tempDirectory.resolve("missing-server/plugins/agent.jar").toString(),
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            RuntimeProtocol.VERSION,
            "agent-cache",
            null,
            List.of()
        );
    }

    private static void setRuntimeLock(DefaultLightkeeperFramework framework, IdeRuntimeLock lock)
        throws ReflectiveOperationException
    {
        final Field lockField = DefaultLightkeeperFramework.class.getDeclaredField("ideRuntimeLock");
        lockField.setAccessible(true);
        lockField.set(framework, lock);
    }
}
