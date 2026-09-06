package nl.pim16aap2.lightkeeper.runtime;

import java.util.List;

/**
 * Integrity and input provenance recorded beside a durable IDE runtime manifest.
 *
 * @param schemaVersion Provenance document schema version.
 * @param moduleDirectory Canonical owning module directory.
 * @param executionId Maven execution that prepared the runtime.
 * @param serverType Selected server platform.
 * @param preparationFingerprint Effective preparation input fingerprint.
 * @param runtimeProtocolVersion Runtime protocol version.
 * @param requiredArtifacts Immutable durable artifacts required at launch.
 * @param sourceInputs Local inputs examined during setup.
 */
public record IdeRuntimeProvenance(
    int schemaVersion,
    String moduleDirectory,
    String executionId,
    String serverType,
    String preparationFingerprint,
    int runtimeProtocolVersion,
    List<Artifact> requiredArtifacts,
    List<Artifact> sourceInputs
)
{
    /** Current provenance document schema version. */
    public static final int SCHEMA_VERSION = 1;

    public IdeRuntimeProvenance
    {
        requiredArtifacts = requiredArtifacts == null ? List.of() : List.copyOf(requiredArtifacts);
        sourceInputs = sourceInputs == null ? List.of() : List.copyOf(sourceInputs);
    }

    /**
     * A path and its canonical content hash.
     *
     * @param path Absolute path recorded at setup.
     * @param sha256 SHA-256 identity for a file or directory tree.
     */
    public record Artifact(String path, String sha256)
    {
    }
}
