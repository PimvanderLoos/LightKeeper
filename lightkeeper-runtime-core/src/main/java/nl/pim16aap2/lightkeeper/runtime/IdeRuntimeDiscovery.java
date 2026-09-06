package nl.pim16aap2.lightkeeper.runtime;

/**
 * Discovery metadata for a reusable IDE test runtime.
 *
 * @param schemaVersion Discovery document schema version.
 * @param moduleDirectory Canonical directory of the Maven module that owns the runtime.
 * @param executionId Maven {@code prepare-server} execution that produced the runtime.
 * @param serverType Selected server platform.
 * @param runtimeManifestPath Absolute path to the durable runtime manifest.
 * @param preparationFingerprint Fingerprint of the effective preparation inputs.
 */
public record IdeRuntimeDiscovery(
    int schemaVersion,
    String moduleDirectory,
    String executionId,
    String serverType,
    String runtimeManifestPath,
    String preparationFingerprint
)
{
    /** Current discovery document schema version. */
    public static final int SCHEMA_VERSION = 1;
}
