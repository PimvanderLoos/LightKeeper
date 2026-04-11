package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeManifestValidatorTest
{
    @Test
    void validateForRuntimeStartup_shouldThrowExceptionWhenProtocolVersionDoesNotMatch(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = Files.createDirectories(tempDirectory.resolve("server"));
        final Path serverJar = Files.writeString(serverDirectory.resolve("paper.jar"), "jar");
        final RuntimeManifest runtimeManifest = createRuntimeManifest(serverDirectory, serverJar, 7);

        // execute + verify
        assertThatThrownBy(() -> RuntimeManifestValidator.validateForRuntimeStartup(runtimeManifest, 8))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Runtime protocol version mismatch");
    }

    @Test
    void validateForRuntimeStartup_shouldThrowExceptionWhenServerDirectoryDoesNotExist(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = tempDirectory.resolve("missing-server");
        final Path serverJar = Files.writeString(tempDirectory.resolve("paper.jar"), "jar");
        final RuntimeManifest runtimeManifest = createRuntimeManifest(serverDirectory, serverJar, 7);

        // execute + verify
        assertThatThrownBy(() -> RuntimeManifestValidator.validateForRuntimeStartup(runtimeManifest, 7))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Server directory");
    }

    @Test
    void validateForRuntimeStartup_shouldThrowExceptionWhenServerJarDoesNotExist(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = Files.createDirectories(tempDirectory.resolve("server"));
        final Path serverJar = serverDirectory.resolve("missing.jar");
        final RuntimeManifest runtimeManifest = createRuntimeManifest(serverDirectory, serverJar, 7);

        // execute + verify
        assertThatThrownBy(() -> RuntimeManifestValidator.validateForRuntimeStartup(runtimeManifest, 7))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Server jar");
    }

    @Test
    void validateForRuntimeStartup_shouldPassWhenManifestMatchesRuntime(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = Files.createDirectories(tempDirectory.resolve("server"));
        final Path serverJar = Files.writeString(serverDirectory.resolve("paper.jar"), "jar");
        final RuntimeManifest runtimeManifest = createRuntimeManifest(serverDirectory, serverJar, 7);

        // execute + verify
        assertThatCode(() -> RuntimeManifestValidator.validateForRuntimeStartup(runtimeManifest, 7))
            .doesNotThrowAnyException();
    }

    private static RuntimeManifest createRuntimeManifest(Path serverDirectory, Path serverJar, int protocolVersion)
    {
        return new RuntimeManifest(
            "paper",
            "1.21.11",
            113,
            "cache-key",
            serverDirectory.toString(),
            serverJar.toString(),
            1024,
            "/tmp/lightkeeper.sock",
            "token",
            null,
            null,
            protocolVersion,
            "agent-cache-id",
            null,
            List.of()
        );
    }
}
