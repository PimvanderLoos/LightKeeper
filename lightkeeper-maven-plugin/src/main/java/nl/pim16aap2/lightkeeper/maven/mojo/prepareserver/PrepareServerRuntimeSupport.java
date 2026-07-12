package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.LightkeeperEmbeddedAgent;
import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestWriter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Handles runtime-specific concerns for the prepare-server flow.
 */
final class PrepareServerRuntimeSupport
{
    private static final int UNIX_SOCKET_PATH_MAX_BYTES = 100;
    private static final Pattern UNRESOLVED_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{[^}]+}");
    private static final Set<PosixFilePermission> USER_ONLY_DIRECTORY_PERMISSIONS = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    );

    private final Log log;
    private final Path defaultSocketDirectory;

    PrepareServerRuntimeSupport(Log log)
    {
        this(
            log,
            resolveDefaultSocketDirectory(
                System.getenv("XDG_RUNTIME_DIR"),
                System.getProperty("user.name"),
                Objects.requireNonNull(System.getProperty("java.io.tmpdir"), "java.io.tmpdir must be set.")
            )
        );
    }

    PrepareServerRuntimeSupport(Log log, Path defaultSocketDirectory)
    {
        this.log = log;
        this.defaultSocketDirectory = defaultSocketDirectory;
    }

    static void ensureRuntimeManifestParentDirectoryExists(Path runtimeManifestPath)
        throws MojoExecutionException
    {
        final Path runtimeManifestParentDirectory = runtimeManifestPath.getParent();
        if (runtimeManifestParentDirectory == null)
            return;

        FileUtil.createDirectories(runtimeManifestParentDirectory, "runtime manifest directory");
    }

    static void validateExtraJvmArgs(@Nullable String extraJvmArgsValue)
        throws MojoExecutionException
    {
        final @Nullable String normalizedExtraJvmArgs =
            PrepareServerInputResolver.normalizeOptionalString(extraJvmArgsValue);
        if (normalizedExtraJvmArgs == null)
            return;

        if (UNRESOLVED_PLACEHOLDER_PATTERN.matcher(normalizedExtraJvmArgs).find())
        {
            throw new MojoExecutionException(
                "Configured 'lightkeeper.extraJvmArgs' contains unresolved Maven placeholder(s): '%s'. "
                    .formatted(normalizedExtraJvmArgs)
                    + "Ensure referenced properties are initialized before 'prepare-server' runs."
            );
        }
    }

    static void writeRuntimeManifest(RuntimeManifest runtimeManifest, Path runtimeManifestPathValue)
        throws MojoExecutionException
    {
        try
        {
            ensureRuntimeManifestParentDirectoryExists(runtimeManifestPathValue);
            new RuntimeManifestWriter().write(runtimeManifest, runtimeManifestPathValue);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to write runtime manifest to '%s'.".formatted(runtimeManifestPathValue),
                exception
            );
        }
    }

    PrepareServerAgentMetadata resolveAgentMetadata()
        throws MojoExecutionException
    {
        final String sha256;
        try (InputStream embeddedAgentStream = LightkeeperEmbeddedAgent.openStream())
        {
            sha256 = HashUtil.sha256(embeddedAgentStream);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException("Failed to close embedded LightKeeper agent stream.", exception);
        }
        return new PrepareServerAgentMetadata(
            sha256,
            LightkeeperEmbeddedAgent.FILE_NAME + ":" + sha256
        );
    }

    /**
     * Resolves the AF_UNIX socket path used by the agent.
     *
     * <p>An explicitly configured directory is used as-is and rejected when the resulting socket path exceeds the
     * AF_UNIX path length limit. Without explicit configuration, a short per-user directory with user-only
     * permissions is used instead.</p>
     *
     * @param configuredDirectory
     *     The explicitly configured socket directory, or {@code null} when not configured.
     * @param agentAuthToken
     *     The agent auth token whose prefix makes the socket file name unique per execution.
     * @return The absolute socket path. Its parent directory exists when this method returns.
     *
     * @throws MojoExecutionException
     *     If the socket path exceeds the AF_UNIX limit or the socket directory cannot be prepared.
     */
    Path resolveUdsSocketPath(@Nullable Path configuredDirectory, String agentAuthToken)
        throws MojoExecutionException
    {
        final String socketFileName = "lk-%s.sock".formatted(agentAuthToken.substring(0, 8));

        if (configuredDirectory != null)
        {
            final Path socketPath = configuredDirectory.resolve(socketFileName).toAbsolutePath();
            requireFitsUnixSocketPath(
                socketPath,
                "Configure a shorter 'agentSocketDirectory' ('lightkeeper.agentSocketDirectory') "
                    + "or remove the setting to use the default per-user directory."
            );
            FileUtil.createDirectories(configuredDirectory, "agent socket directory");
            return socketPath;
        }

        final Path socketPath = defaultSocketDirectory.resolve(socketFileName).toAbsolutePath();
        requireFitsUnixSocketPath(
            socketPath,
            "Configure a short 'agentSocketDirectory' ('lightkeeper.agentSocketDirectory') explicitly."
        );
        prepareUserOnlyDirectory(defaultSocketDirectory);
        log.info("Using agent socket path '%s'.".formatted(socketPath));
        return socketPath;
    }

    /**
     * Determines the default directory for agent sockets.
     *
     * <p>Prefers {@code $XDG_RUNTIME_DIR/lightkeeper} when the runtime directory exists, falling back to a per-user
     * directory under the JVM's temporary directory. Both stay well below the AF_UNIX path length limit, unlike
     * directories nested inside a project's build directory.</p>
     *
     * @param xdgRuntimeDirectory
     *     The value of the {@code XDG_RUNTIME_DIR} environment variable, or {@code null} when unset.
     * @param userName
     *     The name of the current user, or {@code null} when unknown.
     * @param tmpDirectory
     *     The JVM's temporary directory.
     * @return The default socket directory. It may not exist yet.
     */
    static Path resolveDefaultSocketDirectory(
        @Nullable String xdgRuntimeDirectory,
        @Nullable String userName,
        String tmpDirectory)
    {
        if (xdgRuntimeDirectory != null && !xdgRuntimeDirectory.isBlank() &&
            Files.isDirectory(Path.of(xdgRuntimeDirectory)))
            return Path.of(xdgRuntimeDirectory, "lightkeeper").toAbsolutePath();

        final String sanitizedUserName =
            (userName == null || userName.isBlank() ? "user" : userName).replaceAll("[^a-zA-Z0-9._-]", "_");
        return Path.of(tmpDirectory, "lightkeeper-" + sanitizedUserName).toAbsolutePath();
    }

    /**
     * Creates the default socket directory with user-only (0700) permissions and rejects it when it is owned by
     * another user or accessible to other users. This prevents socket squatting when the directory lives in a shared
     * location such as {@code /tmp}.
     */
    private static void prepareUserOnlyDirectory(Path directory)
        throws MojoExecutionException
    {
        if (!directory.getFileSystem().supportedFileAttributeViews().contains("posix"))
        {
            FileUtil.createDirectories(directory, "agent socket directory");
            return;
        }

        try
        {
            Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(USER_ONLY_DIRECTORY_PERMISSIONS));
            verifyUserOnlyDirectory(directory);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to prepare agent socket directory '%s'.".formatted(directory),
                exception
            );
        }
    }

    private static void verifyUserOnlyDirectory(Path directory)
        throws IOException, MojoExecutionException
    {
        final PosixFileAttributes attributes =
            Files.readAttributes(directory, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory())
        {
            throw new MojoExecutionException(
                "Agent socket directory '%s' exists but is not a directory.".formatted(directory)
            );
        }

        final UserPrincipal currentUser = directory.getFileSystem()
            .getUserPrincipalLookupService()
            .lookupPrincipalByName(Objects.requireNonNull(System.getProperty("user.name"), "user.name must be set."));
        if (!attributes.owner().equals(currentUser))
        {
            throw new MojoExecutionException(
                ("Agent socket directory '%s' is owned by '%s' instead of the current user '%s'. "
                    + "Remove it or configure 'agentSocketDirectory' explicitly.")
                    .formatted(directory, attributes.owner().getName(), currentUser.getName())
            );
        }

        if (!attributes.permissions().equals(USER_ONLY_DIRECTORY_PERMISSIONS))
        {
            throw new MojoExecutionException(
                ("Agent socket directory '%s' has permissions '%s' but requires user-only permissions '%s'. "
                    + "Remove it or configure 'agentSocketDirectory' explicitly.")
                    .formatted(
                        directory,
                        PosixFilePermissions.toString(attributes.permissions()),
                        PosixFilePermissions.toString(USER_ONLY_DIRECTORY_PERMISSIONS)
                    )
            );
        }
    }

    private static void requireFitsUnixSocketPath(Path socketPath, String remedy)
        throws MojoExecutionException
    {
        final int pathBytes = socketPath.toString().getBytes(StandardCharsets.UTF_8).length;
        if (pathBytes <= UNIX_SOCKET_PATH_MAX_BYTES)
            return;

        throw new MojoExecutionException(
            "Agent socket path '%s' is %d bytes long, exceeding the AF_UNIX limit of %d bytes. %s"
                .formatted(socketPath, pathBytes, UNIX_SOCKET_PATH_MAX_BYTES, remedy)
        );
    }
}
