package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UserOnlyDirectoryTest
{
    @Test
    void prepare_shouldCreateDirectoryWithUserOnlyPermissions(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        assumePosixFileSystem();
        final Path directory = tempDirectory.resolve("socket");

        // execute
        UserOnlyDirectory.prepare(directory);

        // verify
        assertThat(Files.getPosixFilePermissions(directory)).containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
        );
    }

    @Test
    void prepare_shouldRejectDirectoryAccessibleToOtherUsers(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        assumePosixFileSystem();
        final Path directory = Files.createDirectories(tempDirectory.resolve("socket"));
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-x---"));

        // execute + verify
        assertThatThrownBy(() -> UserOnlyDirectory.prepare(directory))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires user-only permissions 'rwx------'");
    }

    @Test
    void prepare_shouldRejectExistingFile(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path file = Files.writeString(tempDirectory.resolve("socket"), "not-a-directory");

        // execute + verify
        assertThatThrownBy(() -> UserOnlyDirectory.prepare(file))
            .isInstanceOfAny(java.io.IOException.class, IllegalStateException.class);
    }

    @Test
    void prepare_shouldRejectSymbolicLink(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        assumePosixFileSystem();
        final Path target = Files.createDirectories(tempDirectory.resolve("target"));
        final Path link = Files.createSymbolicLink(tempDirectory.resolve("socket"), target);

        // execute + verify
        assertThatThrownBy(() -> UserOnlyDirectory.prepare(link))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is not a directory");
    }

    @Test
    void verify_shouldRejectDirectoryOwnedByDifferentUser(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        assumePosixFileSystem();
        final Path directory = Files.createDirectories(tempDirectory.resolve("socket"));
        final UserPrincipal differentUser = () -> "different-user";

        // execute + verify
        assertThatThrownBy(() -> UserOnlyDirectory.verify(directory, differentUser))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("instead of the current user 'different-user'");
    }

    private static void assumePosixFileSystem()
    {
        assumeTrue(
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
            "POSIX file permissions are not supported on this file system."
        );
    }
}
