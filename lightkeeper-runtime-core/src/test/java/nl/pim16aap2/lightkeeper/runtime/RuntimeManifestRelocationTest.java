package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RuntimeManifestRelocationTest
{
    @Test
    void relocate_shouldRetainMetadataAndReplaceAllOwnedLocations(@TempDir Path root)
    {
        // setup
        final RuntimeManifest original = manifest(root.resolve("original"), true);
        final Path destination = root.resolve("session");
        final Path socket = root.resolve("session.sock");

        // execute
        final RuntimeManifest relocated = original.relocate(destination, socket, "new-token");

        // verify
        assertThat(relocated.serverDirectory()).isEqualTo(destination.toString());
        assertThat(relocated.serverJar()).isEqualTo(destination.resolve("paper.jar").toString());
        assertThat(relocated.agentJar()).isEqualTo(destination.resolve("plugins/agent.jar").toString());
        assertThat(relocated.udsSocketPath()).isEqualTo(socket.toString());
        assertThat(relocated.agentAuthToken()).isEqualTo("new-token");
        assertThat(relocated.extraJvmArgs()).isEqualTo("-Dfixture=true");
        assertThat(relocated.provisionedWorlds()).isEqualTo(original.provisionedWorlds());
        assertThat(relocated.runtimeProtocolVersion()).isEqualTo(RuntimeProtocol.VERSION);
        assertThat(original.agentAuthToken()).isEqualTo("original-token");
    }

    @Test
    void relocate_shouldRetainAbsentAgent(@TempDir Path root)
    {
        // setup
        final RuntimeManifest original = manifest(root.resolve("original"), false);

        // execute
        final RuntimeManifest relocated = original.relocate(root.resolve("session"), root.resolve("s.sock"), "token");

        // verify
        assertThat(relocated.agentJar()).isNull();
    }

    @Test
    void relocate_shouldRejectArtifactEscapingServerDirectory(@TempDir Path root)
    {
        // setup
        final RuntimeManifest original = manifest(root.resolve("original"), true);
        final RuntimeManifest unsafe = new RuntimeManifest(
            original.serverType(), original.serverVersion(), original.paperBuildId(), original.cacheKey(),
            original.serverDirectory(), root.resolve("outside.jar").toString(), original.memoryMb(),
            original.udsSocketPath(), original.agentAuthToken(), original.agentJar(), original.agentJarSha256(),
            original.runtimeProtocolVersion(), original.agentCacheIdentity(), original.extraJvmArgs(),
            original.provisionedWorlds()
        );

        // execute
        final var failure = catchThrowable(() -> unsafe.relocate(root.resolve("session"), root.resolve("s"), "token"));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must be inside");
    }

    @Test
    void relocate_shouldRejectBlankAuthenticationToken(@TempDir Path root)
    {
        // setup
        final RuntimeManifest original = manifest(root.resolve("original"), true);

        // execute
        final var failure = catchThrowable(() -> original.relocate(root.resolve("session"), root.resolve("s"), " "));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("authToken");
    }

    private static RuntimeManifest manifest(Path directory, boolean withAgent)
    {
        return new RuntimeManifest(
            "paper", "1.21.11", 132, "cache", directory.toString(), directory.resolve("paper.jar").toString(),
            1024, directory.resolveSibling("original.sock").toString(), "original-token",
            withAgent ? directory.resolve("plugins/agent.jar").toString() : null, null, RuntimeProtocol.VERSION,
            "agent", "-Dfixture=true", List.of(new RuntimeManifest.ProvisionedWorld("world", "NORMAL", "FLAT", 1, true))
        );
    }
}
