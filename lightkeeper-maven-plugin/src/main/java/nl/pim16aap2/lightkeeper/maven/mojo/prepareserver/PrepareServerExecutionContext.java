package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Immutable inputs resolved from plugin configuration before preparation starts.
 *
 * @param configuredAgentSocketDirectory
 *     The explicitly configured agent socket directory, or {@code null} to use the default per-user directory.
 */
record PrepareServerExecutionContext(
    String normalizedServerType,
    String serverVersion,
    Path jarCacheDirectoryRoot,
    Path baseServerCacheDirectoryRoot,
    Path pluginArtifactCacheDirectoryRoot,
    Path serverWorkDirectoryRoot,
    Path runtimeManifestPath,
    @Nullable Path configuredAgentSocketDirectory,
    String userAgent,
    List<WorldInputSpec> worldInputSpecs,
    List<PluginArtifactSpec> pluginArtifactSpecs
)
{
}
