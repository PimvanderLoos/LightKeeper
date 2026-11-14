package nl.pim16aap2.lightkeeper.provisioning.session;

import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import nl.pim16aap2.lightkeeper.provisioning.serverprovider.TargetServerPropertiesConfigurer;
import nl.pim16aap2.lightkeeper.provisioning.util.FileUtil;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestWriter;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import nl.pim16aap2.lightkeeper.runtime.UserOnlyDirectory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One privately copied mutable server owned by a JUnit class or standalone framework.
 * Stop its server process before closing the session. Restarting a process against {@link #manifestPath()}
 * preserves the session's world and plugin state; another session always starts from the immutable snapshot.
 */
public final class ServerSession implements AutoCloseable
{
    private static final System.Logger LOG = System.getLogger(ServerSession.class.getName());
    /** Private root containing this session's server and diagnostic files. */
    private final Path directory;
    /** Manifest used for every process start within this session. */
    private final Path manifestPath;
    /** Unique socket owned by this session; its name is independent of the authentication token. */
    private final Path socketPath;
    /** Makes cleanup idempotent across normal callbacks and JUnit store shutdown. */
    private final AtomicBoolean closed = new AtomicBoolean();
    /** Failure retention is visible to cleanup on another thread. */
    private volatile boolean preserved;

    private ServerSession(Path directory, Path manifestPath, Path socketPath)
    {
        this.directory = directory;
        this.manifestPath = manifestPath;
        this.socketPath = socketPath;
    }

    /**
     * Materializes an independent runtime from a verified immutable snapshot.
     *
     * @param snapshot
     *     Immutable prepared server inputs.
     * @param sessionsDirectory
     *     LK-owned root for mutable sessions; it must be outside the snapshot server tree.
     * @return A session whose JARs and data are ordinary file copies, with a new socket and token.
     */
    public static ServerSession create(PreparedServerSnapshot snapshot, Path sessionsDirectory)
    {
        final Path source = Path.of(snapshot.manifest().serverDirectory()).toAbsolutePath().normalize();
        final Path root = sessionsDirectory.toAbsolutePath().normalize();
        if (root.startsWith(source))
        {
            throw new IllegalArgumentException("Session output may not be inside its snapshot server directory.");
        }
        final String token = UUID.randomUUID().toString().replace("-", "");
        final String id = UUID.randomUUID().toString().replace("-", "");
        final Path directory = root.resolve(id);
        final Path server = directory.resolve("server");
        final Path socketParent = Objects.requireNonNull(
            Path.of(snapshot.manifest().udsSocketPath()).toAbsolutePath().getParent(),
            "Snapshot socket path must have a parent directory."
        );
        final Path socket = socketParent.resolve("lk-" + id + ".sock");
        if (socket.toString().getBytes(StandardCharsets.UTF_8).length > RuntimeProtocol.UNIX_SOCKET_PATH_MAX_BYTES)
        {
            throw new IllegalArgumentException(
                "Session socket path is too long: '%s'. Configure a shorter agentSocketDirectory.".formatted(socket)
            );
        }
        try
        {
            UserOnlyDirectory.prepare(root);
            UserOnlyDirectory.prepare(directory);
            ServerSnapshots.copyStableServer(source, server, snapshot.serverTreeSha256());
            TargetServerPropertiesConfigurer.rewriteWithRuntimePort(
                LOG,
                server.resolve("server.properties"),
                TargetServerPropertiesConfigurer.reserveAvailableTcpPort()
            );
            final RuntimeManifest manifest = snapshot.manifest().relocate(server, socket, token);
            final Path manifestPath = directory.resolve("runtime-manifest.json");
            new RuntimeManifestWriter().write(manifest, manifestPath);
            return new ServerSession(directory, manifestPath, socket);
        }
        catch (IOException | RuntimeException exception)
        {
            ServerSnapshots.cleanupFailedCopy(directory, exception);
            throw new ProvisioningException("Failed to create server session from '" + source + "'.", exception);
        }
    }

    /**
     * Returns this session's private root, including its server and diagnostics.
     *
     * @return Session directory.
     */
    public Path directory()
    {
        return directory;
    }

    /**
     * Returns the stable manifest used throughout this session's process lifecycle.
     *
     * @return Session manifest path.
     */
    public Path manifestPath()
    {
        return manifestPath;
    }

    /** Keeps this session's server and diagnostic files after a failed test or startup. */
    public void preserve()
    {
        preserved = true;
    }

    /** Deletes this owned session after its process stops, unless retained for failure diagnostics. */
    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            Files.deleteIfExists(socketPath);
        }
        catch (IOException exception)
        {
            throw new ProvisioningException("Failed to remove session socket '" + socketPath + "'.", exception);
        }
        finally
        {
            if (!preserved)
            {
                FileUtil.deleteRecursively(directory, "completed LightKeeper session");
            }
        }
    }
}
