package nl.pim16aap2.lightkeeper.maven.provisioning;

import java.nio.file.Path;

/**
 * Resolved plugin artifact ready for copying to {@code plugins/}.
 *
 * @param sourceJar
 *     Resolved source jar path.
 * @param outputFileName
 *     Output filename in plugins directory.
 * @param sourceDescription
 *     Human-readable source description for logs.
 */
public record ResolvedPluginArtifact(
    Path sourceJar,
    String outputFileName,
    String sourceDescription
)
{
}
