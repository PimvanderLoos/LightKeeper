package nl.pim16aap2.lightkeeper.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Canonical runtime-manifest validation entry point for runtime startup invariants.
 */
public final class RuntimeManifestValidator
{
    private RuntimeManifestValidator()
    {
    }

    /**
     * Validates protocol compatibility and required runtime files before server startup.
     *
     * @param runtimeManifest
     *     Runtime manifest to validate.
     * @param expectedProtocolVersion
     *     Expected runtime protocol version.
     * @throws IllegalStateException
     *     When protocol or runtime file invariants are invalid.
     */
    public static void validateForRuntimeStartup(RuntimeManifest runtimeManifest, int expectedProtocolVersion)
    {
        final RuntimeManifest resolvedManifest =
            Objects.requireNonNull(runtimeManifest, "runtimeManifest may not be null.");
        if (resolvedManifest.runtimeProtocolVersion() != expectedProtocolVersion)
        {
            throw new IllegalStateException(
                "Runtime protocol version mismatch. expected=%d actual=%d."
                    .formatted(expectedProtocolVersion, resolvedManifest.runtimeProtocolVersion())
            );
        }

        final Path serverDirectory = Path.of(resolvedManifest.serverDirectory());
        final Path serverJar = Path.of(resolvedManifest.serverJar());
        if (!Files.isDirectory(serverDirectory))
            throw new IllegalStateException("Server directory '%s' does not exist.".formatted(serverDirectory));
        if (!Files.isRegularFile(serverJar))
            throw new IllegalStateException("Server jar '%s' does not exist.".formatted(serverJar));
    }
}
