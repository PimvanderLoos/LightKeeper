package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeManifestReaderTest
{
    @Test
    void read_shouldThrowExceptionWhenUdsSocketPathIsMissing(@TempDir Path tempDirectory)
        throws IOException
    {
        // setup
        final Path manifestPath = tempDirectory.resolve("runtime-manifest.json");
        Files.writeString(manifestPath, """
            {
              "serverType": "paper",
              "serverVersion": "1.21.11",
              "paperBuildId": 113,
              "cacheKey": "cache-key",
              "serverDirectory": "/tmp/server",
              "serverJar": "/tmp/server/paper.jar",
              "memoryMb": 2048,
              "agentAuthToken": "token",
              "runtimeProtocolVersion": "v1.1",
              "agentCacheIdentity": "no-agent"
            }
            """
        );

        // execute
        final RuntimeManifestReader runtimeManifestReader = new RuntimeManifestReader();

        // verify
        assertThatThrownBy(() -> runtimeManifestReader.read(manifestPath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("udsSocketPath");
    }

    @Test
    void read_shouldParseRuntimeManifestWhenAllRequiredFieldsExist(@TempDir Path tempDirectory)
        throws IOException
    {
        // setup
        final Path manifestPath = tempDirectory.resolve("runtime-manifest.json");
        Files.writeString(manifestPath, """
            {
              "serverType": "paper",
              "serverVersion": "1.21.11",
              "paperBuildId": 113,
              "cacheKey": "cache-key",
              "serverDirectory": "/tmp/server",
              "serverJar": "/tmp/server/paper.jar",
              "memoryMb": 2048,
              "udsSocketPath": "/tmp/lightkeeper.sock",
              "agentAuthToken": "token",
              "runtimeProtocolVersion": "v1.1",
              "agentCacheIdentity": "no-agent"
            }
            """
        );

        // execute
        final RuntimeManifest runtimeManifest = new RuntimeManifestReader().read(manifestPath);

        // verify
        assertThat(runtimeManifest.serverType()).isEqualTo("paper");
        assertThat(runtimeManifest.udsSocketPath()).isEqualTo("/tmp/lightkeeper.sock");
        assertThat(runtimeManifest.memoryMb()).isEqualTo(2048);
        assertThat(runtimeManifest.preloadedWorlds()).isEmpty();
    }

    @Test
    void read_shouldParsePreloadedWorldsWhenPresent(@TempDir Path tempDirectory)
        throws IOException
    {
        // setup
        final Path manifestPath = tempDirectory.resolve("runtime-manifest.json");
        Files.writeString(manifestPath, """
            {
              "serverType": "paper",
              "serverVersion": "1.21.11",
              "paperBuildId": 113,
              "cacheKey": "cache-key",
              "serverDirectory": "/tmp/server",
              "serverJar": "/tmp/server/paper.jar",
              "memoryMb": 2048,
              "udsSocketPath": "/tmp/lightkeeper.sock",
              "agentAuthToken": "token",
              "runtimeProtocolVersion": "v1.1",
              "agentCacheIdentity": "no-agent",
              "preloadedWorlds": [
                {
                  "name": "fixture-world",
                  "environment": "NORMAL",
                  "worldType": "FLAT",
                  "seed": 42
                }
              ]
            }
            """
        );

        // execute
        final RuntimeManifest runtimeManifest = new RuntimeManifestReader().read(manifestPath);

        // verify
        assertThat(runtimeManifest.preloadedWorlds()).hasSize(1);
        assertThat(runtimeManifest.preloadedWorlds().getFirst().name()).isEqualTo("fixture-world");
        assertThat(runtimeManifest.preloadedWorlds().getFirst().worldType()).isEqualTo("FLAT");
    }
}
