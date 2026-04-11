package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PrepareServerRuntimeSupportTest
{
    @Test
    void resolveAgentMetadata_shouldResolveEmbeddedAgentMetadata()
        throws Exception
    {
        // setup
        final PrepareServerRuntimeSupport runtimeSupport = new PrepareServerRuntimeSupport(new SystemStreamLog());

        // execute
        final PrepareServerAgentMetadata metadata = runtimeSupport.resolveAgentMetadata();

        // verify
        assertThat(metadata.cacheIdentity()).startsWith("lightkeeper-agent-spigot.jar:");
        assertThat(metadata.sha256()).matches("[a-f0-9]{64}");
    }

    @Test
    void resolveUdsSocketPath_shouldReturnPreferredSocketWhenPathFits(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerRuntimeSupport runtimeSupport = new PrepareServerRuntimeSupport(new SystemStreamLog());

        // execute
        final Path socketPath = runtimeSupport.resolveUdsSocketPath(tempDirectory, "abcdef0123456789abcdef0123456789");

        // verify
        assertThat(socketPath.toString()).startsWith(tempDirectory.toAbsolutePath().toString());
    }

    @Test
    void writeRuntimeManifest_shouldThrowExceptionWhenParentPathIsAFile(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path parentFile = Files.writeString(tempDirectory.resolve("blocked-parent"), "x");
        final Path manifestPath = parentFile.resolve("runtime-manifest.json");
        final RuntimeManifest runtimeManifest = new RuntimeManifest(
            "paper",
            "1.21.11",
            116L,
            "cache-key",
            tempDirectory.resolve("server").toString(),
            tempDirectory.resolve("server/paper.jar").toString(),
            512,
            tempDirectory.resolve("socket.sock").toString(),
            "auth-token",
            null,
            null,
            1,
            "no-agent",
            null,
            List.of()
        );

        // execute + verify
        assertThatThrownBy(() -> PrepareServerRuntimeSupport.writeRuntimeManifest(runtimeManifest, manifestPath))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("not a directory");
    }
}
