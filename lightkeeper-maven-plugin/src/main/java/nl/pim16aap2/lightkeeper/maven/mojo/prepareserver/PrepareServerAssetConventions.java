package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.provisioning.LightkeeperEmbeddedAgent;
import nl.pim16aap2.lightkeeper.provisioning.assets.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.provisioning.assets.ResolvedPluginArtifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic module-local fixture discovery and protection of selected plugin/agent outputs. */
final class PrepareServerAssetConventions
{
    private PrepareServerAssetConventions()
    {
    }

    static List<PluginArtifactSpec> plugins(Path module, List<PluginArtifactSpec> explicit)
        throws MojoExecutionException
    {
        final Path directory = module.resolve("src/test/lightkeeper/plugins");
        if (Files.notExists(directory))
        {
            return explicit;
        }
        final List<PluginArtifactSpec> result = new ArrayList<>(explicit);
        final Set<Path> existing = new HashSet<>();
        for (final PluginArtifactSpec spec : explicit)
        {
            final Path sourcePath = spec.path();
            if (sourcePath != null)
            {
                existing.add(sourcePath.toAbsolutePath().normalize());
            }
        }
        try (var children = Files.list(directory))
        {
            for (final Path path : children.sorted().toList())
            {
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar") &&
                    existing.add(path.toAbsolutePath().normalize()))
                {
                    result.add(new PluginArtifactSpec(
                        PluginArtifactSpec.SourceType.PATH,
                        path.toAbsolutePath().normalize(),
                        null,
                        null,
                        null,
                        null,
                        "jar",
                        false,
                        null
                    ));
                }
            }
            return List.copyOf(result);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Cannot read conventional plugin directory '" + directory + "'.", exception
            );
        }
    }

    static @Nullable Path configOverlay(Path module, @Nullable Path explicit)
        throws MojoExecutionException
    {
        final Path selected = explicit == null ? module.resolve("src/test/lightkeeper/config") :
            (explicit.isAbsolute() ? explicit : module.resolve(explicit));
        if (explicit == null && Files.notExists(selected))
        {
            return null;
        }
        if (!Files.isDirectory(selected))
        {
            throw new MojoExecutionException("Configuration overlay is not a directory: '" + selected + "'.");
        }
        return selected.toAbsolutePath().normalize();
    }

    static List<ResolvedPluginArtifact> uniquePlugins(
        List<ResolvedPluginArtifact> plugins,
        @Nullable Path overlay
    )
        throws MojoExecutionException
    {
        final var outputs = new LinkedHashMap<String, ResolvedPluginArtifact>();
        for (final ResolvedPluginArtifact plugin : plugins)
        {
            final String name = plugin.outputFileName().toLowerCase(Locale.ROOT);
            if (name.equals(LightkeeperEmbeddedAgent.FILE_NAME))
            {
                throw new MojoExecutionException(
                    "Fixture plugin cannot replace the LightKeeper agent: " + plugin.sourceJar()
                );
            }
            final ResolvedPluginArtifact previous = outputs.putIfAbsent(name, plugin);
            if (previous != null && !sameFile(previous.sourceJar(), plugin.sourceJar()))
            {
                throw new MojoExecutionException("Conflicting plugin output '" + name + "': "
                    + previous.sourceJar() + " and " + plugin.sourceJar() + ". Use distinct renameTo values.");
            }
            guardOverlay(overlay, plugin.outputFileName());
        }
        guardOverlay(overlay, LightkeeperEmbeddedAgent.FILE_NAME);
        return List.copyOf(outputs.values());
    }

    private static boolean sameFile(Path first, Path second)
        throws MojoExecutionException
    {
        try
        {
            return Files.isSameFile(first, second);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Cannot compare fixture inputs '" + first + "' and '" + second + "'.", exception
            );
        }
    }

    private static void guardOverlay(@Nullable Path overlay, String outputName)
        throws MojoExecutionException
    {
        if (overlay != null && Files.exists(overlay.resolve("plugins").resolve(outputName)))
        {
            throw new MojoExecutionException(
                "Config overlay cannot replace selected plugin/agent '" + outputName + "'."
            );
        }
    }
}
