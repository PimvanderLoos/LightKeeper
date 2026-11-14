package nl.pim16aap2.lightkeeper.provisioning.session;

import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;

import java.util.Objects;

/**
 * Immutable prepared inputs copied before any test process starts.
 *
 * @param schemaVersion
 *     Snapshot metadata format version.
 * @param manifest
 *     Manifest describing the private prepared server tree.
 * @param serverTreeSha256
 *     Canonical SHA-256 of the complete prepared server tree.
 */
public record PreparedServerSnapshot(
    int schemaVersion,
    RuntimeManifest manifest,
    String serverTreeSha256
)
{
    /** Supported snapshot metadata format. */
    public static final int SCHEMA_VERSION = 1;

    /** Validates the immutable snapshot metadata. */
    public PreparedServerSnapshot
    {
        if (schemaVersion != SCHEMA_VERSION)
        {
            throw new IllegalArgumentException("Unsupported prepared snapshot schema: " + schemaVersion);
        }
        Objects.requireNonNull(manifest, "manifest may not be null.");
        if (!Objects.requireNonNull(serverTreeSha256, "serverTreeSha256 may not be null.").matches("[a-f0-9]{64}"))
        {
            throw new IllegalArgumentException("serverTreeSha256 must contain a SHA-256 digest.");
        }
    }
}
