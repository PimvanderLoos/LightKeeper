package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    @Test
    void validateForRuntimeStartup_shouldRecreateMissingSocketDirectoryWithUserOnlyPermissions(
        @TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        assumeTrue(tempDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"));
        final Path serverDirectory = Files.createDirectories(tempDirectory.resolve("server"));
        final Path serverJar = Files.writeString(serverDirectory.resolve("paper.jar"), "jar");
        final Path socketDirectory = tempDirectory.resolve("socket");
        final RuntimeManifest runtimeManifest = createRuntimeManifest(
            serverDirectory,
            serverJar,
            socketDirectory.resolve("lightkeeper.sock"),
            7
        );

        // execute
        RuntimeManifestValidator.validateForRuntimeStartup(runtimeManifest, 7);

        // verify
        assertThat(Files.getPosixFilePermissions(socketDirectory)).containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
        );
    }

    @Test
    void validateForRuntimeStartup_shouldThrowExceptionWhenSocketPathHasNoParent(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = Files.createDirectories(tempDirectory.resolve("server"));
        final Path serverJar = Files.writeString(serverDirectory.resolve("paper.jar"), "jar");
        final RuntimeManifest runtimeManifest =
            createRuntimeManifest(serverDirectory, serverJar, Path.of("lightkeeper.sock"), 7);

        // execute + verify
        assertThatThrownBy(() -> RuntimeManifestValidator.validateForRuntimeStartup(runtimeManifest, 7))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("has no parent directory");
    }

    @Test
    void validateForRuntimeStartup_shouldThrowExceptionWhenSocketDirectoryCannotBeCreated(
        @TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = Files.createDirectories(tempDirectory.resolve("server"));
        final Path serverJar = Files.writeString(serverDirectory.resolve("paper.jar"), "jar");
        final Path parentFile = Files.writeString(tempDirectory.resolve("socket-parent"), "file");
        final RuntimeManifest runtimeManifest =
            createRuntimeManifest(serverDirectory, serverJar, parentFile.resolve("lightkeeper.sock"), 7);

        // execute + verify
        assertThatThrownBy(() -> RuntimeManifestValidator.validateForRuntimeStartup(runtimeManifest, 7))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot be prepared")
            .hasCauseInstanceOf(java.io.IOException.class);
    }

    private static RuntimeManifest createRuntimeManifest(Path serverDirectory, Path serverJar, int protocolVersion)
    {
        return createRuntimeManifest(serverDirectory, serverJar, Path.of("/tmp/lightkeeper.sock"), protocolVersion);
    }

    private static RuntimeManifest createRuntimeManifest(
        Path serverDirectory,
        Path serverJar,
        Path socketPath,
        int protocolVersion)
    {
        return new RuntimeManifest(
            "paper",
            "1.21.11",
            113,
            "cache-key",
            serverDirectory.toString(),
            serverJar.toString(),
            1024,
            socketPath.toString(),
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
