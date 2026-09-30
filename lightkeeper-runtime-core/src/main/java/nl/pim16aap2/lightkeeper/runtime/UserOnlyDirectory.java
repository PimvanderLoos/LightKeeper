package nl.pim16aap2.lightkeeper.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Objects;
import java.util.Set;

/**
 * Creates and validates directories that must only be accessible to the current operating-system user.
 */
public final class UserOnlyDirectory
{
    private static final Set<PosixFilePermission> USER_ONLY_PERMISSIONS = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    );

    private UserOnlyDirectory()
    {
    }

    /**
     * Creates a directory with {@code 0700} permissions and verifies its owner and permissions on POSIX file systems.
     * Non-POSIX file systems receive normal directory creation because they do not expose equivalent attributes.
     *
     * @param directory Directory to create or validate.
     * @throws IOException When the directory or its attributes cannot be accessed.
     * @throws IllegalStateException When an existing POSIX path is not a user-only directory owned by the current user.
     */
    public static void prepare(Path directory)
        throws IOException
    {
        final Path resolvedDirectory = Objects.requireNonNull(directory, "directory may not be null.");
        if (!resolvedDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"))
        {
            Files.createDirectories(resolvedDirectory);
            return;
        }

        Files.createDirectories(
            resolvedDirectory,
            PosixFilePermissions.asFileAttribute(USER_ONLY_PERMISSIONS)
        );
        final UserPrincipal currentUser = resolvedDirectory.getFileSystem()
            .getUserPrincipalLookupService()
            .lookupPrincipalByName(Objects.requireNonNull(System.getProperty("user.name"), "user.name must be set."));
        verify(resolvedDirectory, currentUser);
    }

    static void verify(Path directory, UserPrincipal currentUser)
        throws IOException
    {
        final PosixFileAttributes attributes =
            Files.readAttributes(directory, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory())
            throw new IllegalStateException("User-only path '%s' is not a directory.".formatted(directory));

        if (!attributes.owner().equals(currentUser))
        {
            throw new IllegalStateException(
                "User-only directory '%s' is owned by '%s' instead of the current user '%s'."
                    .formatted(directory, attributes.owner().getName(), currentUser.getName())
            );
        }

        if (!attributes.permissions().equals(USER_ONLY_PERMISSIONS))
        {
            throw new IllegalStateException(
                "User-only directory '%s' has permissions '%s' but requires user-only permissions '%s'."
                    .formatted(
                        directory,
                        PosixFilePermissions.toString(attributes.permissions()),
                        PosixFilePermissions.toString(USER_ONLY_PERMISSIONS)
                    )
            );
        }
    }
}
