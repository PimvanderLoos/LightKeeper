package nl.pim16aap2.lightkeeper.runtime;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One atomic module/variant export tied to its real Maven producer process and immutable snapshot.
 * A live ancestor identity allows Failsafe to consume the current build without another producer invocation.
 * A direct bootstrap consumes only its own matching export nonce after its child build succeeds.
 *
 * @param schemaVersion
 *     Runtime export format version.
 * @param plan
 *     Effective variant prepared by Maven.
 * @param snapshotMetadataPath
 *     Immutable snapshot metadata owned by this module.
 * @param producer
 *     Maven JVM's PID and start instant; this is checked against actual ancestry at launch.
 * @param exportNonce
 *     Current invocation/export identity, never a persistent Maven-mode flag.
 */
public record MavenRuntimeContext(
    int schemaVersion,
    EffectiveTestPlan plan,
    String snapshotMetadataPath,
    ProcessIdentity producer,
    String exportNonce
)
{
    /** Supported runtime export format. */
    public static final int SCHEMA_VERSION = 1;

    /** Validates export format, snapshot ownership and invocation identity. */
    public MavenRuntimeContext
    {
        if (schemaVersion != SCHEMA_VERSION)
        {
            throw new IllegalArgumentException("Unsupported Maven runtime context schema: " + schemaVersion);
        }
        Objects.requireNonNull(plan, "plan may not be null.");
        Objects.requireNonNull(producer, "producer may not be null.");
        snapshotMetadataPath = ModulePlanPaths.absolutePath(snapshotMetadataPath, "snapshotMetadataPath");
        final Path snapshots = ModulePlanPaths.snapshotsDirectory(Path.of(plan.moduleDirectory()));
        final Path snapshot = Path.of(snapshotMetadataPath);
        if (!snapshot.startsWith(snapshots) || snapshot.equals(snapshots))
        {
            throw new IllegalArgumentException("Runtime snapshot must belong to the context's owning module.");
        }
        if (!Objects.requireNonNull(exportNonce, "exportNonce may not be null.").matches("[a-f0-9]{32}"))
        {
            throw new IllegalArgumentException("Runtime context requires a 128-bit invocation identity.");
        }
    }
}
