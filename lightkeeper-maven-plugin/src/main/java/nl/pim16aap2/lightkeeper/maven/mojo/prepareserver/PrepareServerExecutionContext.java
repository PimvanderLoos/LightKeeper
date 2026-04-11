package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;

import java.nio.file.Path;
import java.util.List;

/**
 * Immutable inputs resolved from plugin configuration before preparation starts.
 */
record PrepareServerExecutionContext(
    String normalizedServerType,
    String serverVersion,
    Path jarCacheDirectoryRoot,
    Path baseServerCacheDirectoryRoot,
    Path serverWorkDirectoryRoot,
    Path runtimeManifestPath,
    Path agentSocketDirectory,
    String userAgent,
    List<WorldInputSpec> worldInputSpecs,
    List<PluginArtifactSpec> pluginArtifactSpecs
)
{
}
