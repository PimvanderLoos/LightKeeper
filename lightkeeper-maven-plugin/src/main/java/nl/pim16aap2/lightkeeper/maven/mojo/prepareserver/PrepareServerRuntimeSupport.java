package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestWriter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Handles runtime-specific concerns for the prepare-server flow.
 */
final class PrepareServerRuntimeSupport
{
    private static final int UNIX_SOCKET_PATH_MAX_BYTES = 100;
    private static final Pattern UNRESOLVED_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{[^}]+}");

    private final Log log;

    PrepareServerRuntimeSupport(Log log)
    {
        this.log = log;
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

    PrepareServerAgentMetadata resolveAgentMetadata(@Nullable Path path)
        throws MojoExecutionException
    {
        if (path == null)
            return new PrepareServerAgentMetadata(null, "no-agent");

        if (Files.notExists(path) || !Files.isRegularFile(path))
            throw new MojoExecutionException("Configured agent jar path '%s' does not exist.".formatted(path));

        final String sha256 = HashUtil.sha256(path);
        final String fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        return new PrepareServerAgentMetadata(sha256, fileName + ":" + sha256);
    }

    Path resolveUdsSocketPath(Path preferredDirectory, String agentAuthToken)
        throws MojoExecutionException
    {
        final String socketFileName = "lk-%s.sock".formatted(agentAuthToken.substring(0, 8));
        final Path preferredPath = preferredDirectory.resolve(socketFileName).toAbsolutePath();
        if (fitsUnixSocketPath(preferredPath))
            return preferredPath;

        final Path fallbackDirectory = Path.of(System.getProperty("java.io.tmpdir"), "lightkeeper-sockets");
        FileUtil.createDirectories(fallbackDirectory, "fallback agent socket directory");

        final Path fallbackPath = fallbackDirectory.resolve(socketFileName).toAbsolutePath();
        if (fitsUnixSocketPath(fallbackPath))
        {
            log.warn(
                "Configured socket path is too long for AF_UNIX. Falling back to short path '%s'."
                    .formatted(fallbackPath)
            );
            return fallbackPath;
        }

        throw new MojoExecutionException(
            "Unable to generate a valid AF_UNIX socket path. Tried '%s' and '%s'."
                .formatted(preferredPath, fallbackPath)
        );
    }

    private static boolean fitsUnixSocketPath(Path path)
    {
        return path.toString().getBytes(StandardCharsets.UTF_8).length <= UNIX_SOCKET_PATH_MAX_BYTES;
    }
}
