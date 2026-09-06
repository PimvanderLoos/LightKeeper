package nl.pim16aap2.lightkeeper.runtime;

import java.nio.file.Path;

/**
 * Validated module-owned discovery selection for a framework launch.
 *
 * @param moduleDirectory Owning Maven module directory.
 * @param runtimeManifestPath Durable runtime manifest path.
 * @param discovery Discovery metadata selecting the runtime.
 */
public record IdeRuntimeSelection(
    Path moduleDirectory,
    Path runtimeManifestPath,
    IdeRuntimeDiscovery discovery
)
{
}
