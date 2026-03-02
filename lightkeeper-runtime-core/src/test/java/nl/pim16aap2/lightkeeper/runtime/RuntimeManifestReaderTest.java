package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeManifestReaderTest
{
    @Test
    void read_shouldThrowExceptionWhenManifestDoesNotExist(@TempDir Path tempDirectory)
    {
        // setup
        final Path missingManifestPath = tempDirectory.resolve("missing-runtime-manifest.json");

        // execute + verify
        assertThatThrownBy(() -> new RuntimeManifestReader().read(missingManifestPath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("does not exist");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "serverType",
        "serverVersion",
        "cacheKey",
        "serverDirectory",
        "serverJar",
        "udsSocketPath",
        "agentAuthToken",
        "runtimeProtocolVersion",
        "agentCacheIdentity"
    })
    void read_shouldThrowExceptionWhenRequiredStringFieldIsBlank(
        String fieldName,
        @TempDir Path tempDirectory)
        throws IOException
    {
        // setup
        final Path manifestPath = tempDirectory.resolve("runtime-manifest.json");
        final String manifestContents = """
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
            """.replace("\"%s\": \"%s\"".formatted(fieldName, defaultValueFor(fieldName)),
            "\"%s\": \"\"".formatted(fieldName));
        Files.writeString(manifestPath, manifestContents);

        // execute + verify
        assertThatThrownBy(() -> new RuntimeManifestReader().read(manifestPath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining(fieldName);
    }

    @Test
    void read_shouldThrowExceptionWhenMemoryIsInvalid(@TempDir Path tempDirectory)
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
              "memoryMb": 0,
              "udsSocketPath": "/tmp/lightkeeper.sock",
              "agentAuthToken": "token",
              "runtimeProtocolVersion": "v1.1",
              "agentCacheIdentity": "no-agent"
            }
            """
        );

        // execute + verify
        assertThatThrownBy(() -> new RuntimeManifestReader().read(manifestPath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("memoryMb");
    }

    @Test
    void read_shouldThrowExceptionWhenPreloadedWorldNameIsBlank(@TempDir Path tempDirectory)
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
                  "name": "",
                  "environment": "NORMAL",
                  "worldType": "FLAT",
                  "seed": 42
                }
              ]
            }
            """
        );

        // execute + verify
        assertThatThrownBy(() -> new RuntimeManifestReader().read(manifestPath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("missing name");
    }

    @Test
    void read_shouldThrowExceptionWhenPreloadedWorldEnvironmentIsBlank(@TempDir Path tempDirectory)
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
                  "name": "fixture",
                  "environment": "",
                  "worldType": "FLAT",
                  "seed": 42
                }
              ]
            }
            """
        );

        // execute + verify
        assertThatThrownBy(() -> new RuntimeManifestReader().read(manifestPath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("missing environment");
    }

    @Test
    void read_shouldThrowExceptionWhenPreloadedWorldTypeIsBlank(@TempDir Path tempDirectory)
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
                  "name": "fixture",
                  "environment": "NORMAL",
                  "worldType": "",
                  "seed": 42
                }
              ]
            }
            """
        );

        // execute + verify
        assertThatThrownBy(() -> new RuntimeManifestReader().read(manifestPath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("missing worldType");
    }

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

    private static String defaultValueFor(String fieldName)
    {
        return switch (fieldName)
        {
            case "serverType" -> "paper";
            case "serverVersion" -> "1.21.11";
            case "cacheKey" -> "cache-key";
            case "serverDirectory" -> "/tmp/server";
            case "serverJar" -> "/tmp/server/paper.jar";
            case "udsSocketPath" -> "/tmp/lightkeeper.sock";
            case "agentAuthToken" -> "token";
            case "runtimeProtocolVersion" -> "v1.1";
            case "agentCacheIdentity" -> "no-agent";
            default -> "";
        };
    }
}
