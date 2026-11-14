package nl.pim16aap2.lightkeeper.provisioning.session;

import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import nl.pim16aap2.lightkeeper.provisioning.util.FileUtil;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeArtifactHasher;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestValidator;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import nl.pim16aap2.lightkeeper.runtime.UserOnlyDirectory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Captures complete prepared server inputs and atomically publishes their metadata.
 * Callers hold the producer/build lease until capture returns; hashes reject unstable inputs rather than
 * treating an existing artifact as proof that its sources are current.
 */
public final class ServerSnapshots
{
    /** Metadata stored beside each immutable server tree. */
    public static final String METADATA_FILE = "snapshot.json";
    private static final long MAX_METADATA_BYTES = 1_048_576;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ServerSnapshots()
    {
    }

    /**
     * Copies a stopped, prepared server into a new immutable snapshot.
     *
     * @param manifest
     *     Prepared server to capture; its JARs must be inside its server directory.
     * @param snapshotsDirectory
     *     LK-owned directory receiving the unique snapshot.
     * @return Path to the published snapshot metadata.
     */
    public static Path capture(RuntimeManifest manifest, Path snapshotsDirectory)
    {
        RuntimeManifestValidator.validateForRuntimeStartup(manifest, RuntimeProtocol.VERSION);
        final Path source = Path.of(manifest.serverDirectory()).toAbsolutePath().normalize();
        final Path root = snapshotsDirectory.toAbsolutePath().normalize();
        if (root.startsWith(source))
        {
            throw new IllegalArgumentException("Snapshot output may not be inside its source server directory.");
        }
        final String id = UUID.randomUUID().toString();
        final Path pending = root.resolve(".pending-" + id);
        final Path published = root.resolve(id);
        final RuntimeManifest relocated = manifest.relocate(
            published.resolve("server"),
            Path.of(manifest.udsSocketPath()),
            manifest.agentAuthToken()
        );
        try
        {
            UserOnlyDirectory.prepare(root);
            UserOnlyDirectory.prepare(pending);
            final String hash = IdeRuntimeArtifactHasher.sha256(source);
            copyStableServer(source, pending.resolve("server"), hash);
            final PreparedServerSnapshot snapshot = new PreparedServerSnapshot(
                PreparedServerSnapshot.SCHEMA_VERSION,
                relocated,
                hash
            );
            MAPPER.writeValue(pending.resolve(METADATA_FILE).toFile(), snapshot);
            publish(pending, published);
            return published.resolve(METADATA_FILE);
        }
        catch (IOException | RuntimeException exception)
        {
            cleanupFailedCopy(pending, exception);
            throw new ProvisioningException(
                "Failed to capture prepared server snapshot from '" + source + "'.",
                exception
            );
        }
    }

    /**
     * Reads published snapshot metadata without inferring any producer build or fallback runtime.
     *
     * @param metadataPath
     *     Snapshot metadata produced by {@link #capture}.
     * @return Validated metadata; session materialization verifies the actual tree contents.
     */
    public static PreparedServerSnapshot read(Path metadataPath)
    {
        final Path metadata = metadataPath.toAbsolutePath().normalize();
        try
        {
            if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS) || Files.size(metadata) > MAX_METADATA_BYTES)
            {
                throw new IOException("Missing or oversized snapshot metadata: " + metadata);
            }
            final PreparedServerSnapshot snapshot = MAPPER.readValue(metadata.toFile(), PreparedServerSnapshot.class);
            final Path expected = Objects.requireNonNull(metadata.getParent(), "Snapshot metadata must have a parent.")
                .resolve("server");
            if (!Path.of(snapshot.manifest().serverDirectory()).equals(expected))
            {
                throw new IOException("Snapshot server directory does not belong to its metadata: " + metadata);
            }
            snapshot.manifest().relocate(expected, Path.of(snapshot.manifest().udsSocketPath()), "validation");
            return snapshot;
        }
        catch (IOException | RuntimeException exception)
        {
            throw new ProvisioningException("Failed to read prepared server snapshot '" + metadata + "'.", exception);
        }
    }

    static void copyStableServer(Path source, Path destination, String expectedHash)
        throws IOException
    {
        if (!expectedHash.equals(IdeRuntimeArtifactHasher.sha256(source)))
        {
            throw new IOException("Prepared server snapshot changed before copying: " + source);
        }
        FileUtil.copyDirectoryRecursively(source, destination);
        if (!expectedHash.equals(IdeRuntimeArtifactHasher.sha256(destination)) ||
            !expectedHash.equals(IdeRuntimeArtifactHasher.sha256(source)))
        {
            throw new IOException("Prepared server inputs changed while copying: " + source);
        }
    }

    static void cleanupFailedCopy(Path directory, Exception failure)
    {
        try
        {
            FileUtil.deleteRecursively(directory, "incomplete prepared server copy");
        }
        catch (RuntimeException cleanupFailure)
        {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void publish(Path pending, Path published)
        throws IOException
    {
        try
        {
            Files.move(pending, published, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException exception)
        {
            Files.move(pending, published);
        }
    }
}
