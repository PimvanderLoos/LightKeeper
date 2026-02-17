package nl.pim16aap2.lightkeeper.maven;

import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import org.apache.maven.plugin.MojoExecutionException;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves and validates world/plugin input sections for {@code prepare-server}.
 */
final class PrepareServerInputResolver
{
    List<WorldInputSpec> resolveWorldInputSpecs(@Nullable List<PrepareServerWorldInputConfig> configuredWorlds)
        throws MojoExecutionException
    {
        final List<WorldInputSpec> inputSpecs = new ArrayList<>();
        final List<PrepareServerWorldInputConfig> worlds = configuredWorlds == null ? List.of() : configuredWorlds;
        for (final PrepareServerWorldInputConfig worldInputConfig : worlds)
        {
            if (worldInputConfig == null)
                throw new MojoExecutionException("Configured world entry may not be null.");

            final String worldName = validateWorldName(worldInputConfig.name());
            final WorldInputSpec.SourceType sourceType = parseWorldSourceType(worldInputConfig.sourceType());
            if (worldInputConfig.sourcePath() == null)
            {
                throw new MojoExecutionException(
                    "Missing required configuration value 'lightkeeper.worlds.sourcePath'."
                );
            }
            final Path sourcePath = worldInputConfig.sourcePath().toAbsolutePath().normalize();

            if (sourceType == WorldInputSpec.SourceType.FOLDER && !Files.isDirectory(sourcePath))
            {
                throw new MojoExecutionException(
                    "Configured world '%s' requires a directory sourcePath, but '%s' is not a directory."
                        .formatted(worldName, sourcePath)
                );
            }
            if (sourceType == WorldInputSpec.SourceType.ARCHIVE && !Files.isRegularFile(sourcePath))
            {
                throw new MojoExecutionException(
                    "Configured world '%s' requires a file archive sourcePath, but '%s' is not a regular file."
                        .formatted(worldName, sourcePath)
                );
            }

            inputSpecs.add(new WorldInputSpec(
                worldName,
                sourceType,
                sourcePath,
                worldInputConfig.overwrite() == null || worldInputConfig.overwrite(),
                worldInputConfig.loadOnStartup() == null || worldInputConfig.loadOnStartup(),
                normalizeWorldEnvironment(worldInputConfig.environment()),
                normalizeWorldType(worldInputConfig.worldType()),
                worldInputConfig.seed() == null ? 0L : worldInputConfig.seed()
            ));
        }

        return inputSpecs;
    }

    List<PluginArtifactSpec> resolvePluginArtifactSpecs(
        @Nullable List<PrepareServerPluginArtifactConfig> configuredPlugins
    )
        throws MojoExecutionException
    {
        final List<PluginArtifactSpec> specs = new ArrayList<>();
        final List<PrepareServerPluginArtifactConfig> plugins = configuredPlugins == null ? List.of() :
            configuredPlugins;
        for (final PrepareServerPluginArtifactConfig pluginArtifactConfig : plugins)
        {
            if (pluginArtifactConfig == null)
                throw new MojoExecutionException("Configured plugin entry may not be null.");

            final PluginArtifactSpec.SourceType sourceType = parsePluginSourceType(pluginArtifactConfig.sourceType());
            final String renameTo = normalizeOptionalPluginFileName(pluginArtifactConfig.renameTo());

            if (sourceType == PluginArtifactSpec.SourceType.PATH)
            {
                if (pluginArtifactConfig.path() == null)
                    throw new MojoExecutionException(
                        "Missing required configuration value 'lightkeeper.plugins.path'."
                    );
                final Path path = pluginArtifactConfig.path().toAbsolutePath().normalize();
                if (!Files.isRegularFile(path))
                {
                    throw new MojoExecutionException(
                        "Configured plugin path source '%s' does not exist as a regular file."
                            .formatted(path)
                    );
                }

                specs.add(new PluginArtifactSpec(
                    sourceType,
                    path,
                    null,
                    null,
                    null,
                    null,
                    "jar",
                    false,
                    renameTo
                ));
                continue;
            }

            final String groupId = requireNonBlank(pluginArtifactConfig.groupId(), "lightkeeper.plugins.groupId");
            final String artifactId = requireNonBlank(
                pluginArtifactConfig.artifactId(),
                "lightkeeper.plugins.artifactId"
            );
            final String version = requireNonBlank(pluginArtifactConfig.version(), "lightkeeper.plugins.version");
            final String classifier = normalizeOptionalString(pluginArtifactConfig.classifier());
            final @Nullable String configuredType = normalizeOptionalString(pluginArtifactConfig.type());
            final String type = configuredType == null ? "jar" : configuredType;
            final boolean includeTransitive = pluginArtifactConfig.includeTransitive() != null &&
                pluginArtifactConfig.includeTransitive();

            if (includeTransitive && renameTo != null)
            {
                throw new MojoExecutionException(
                    "Configured plugin '%s:%s:%s' cannot set renameTo when includeTransitive=true."
                        .formatted(groupId, artifactId, version)
                );
            }

            specs.add(new PluginArtifactSpec(
                sourceType,
                null,
                groupId,
                artifactId,
                version,
                classifier,
                type,
                includeTransitive,
                renameTo
            ));
        }

        return specs;
    }

    private static WorldInputSpec.SourceType parseWorldSourceType(@Nullable String sourceType)
        throws MojoExecutionException
    {
        final String normalized = requireNonBlank(sourceType, "lightkeeper.worlds.sourceType");
        try
        {
            return WorldInputSpec.SourceType.valueOf(normalized.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception)
        {
            throw new MojoExecutionException(
                "Unsupported world sourceType '%s'. Supported values: %s"
                    .formatted(sourceType, List.of(WorldInputSpec.SourceType.values())),
                exception
            );
        }
    }

    private static PluginArtifactSpec.SourceType parsePluginSourceType(@Nullable String sourceType)
        throws MojoExecutionException
    {
        final String normalized = requireNonBlank(sourceType, "lightkeeper.plugins.sourceType");
        try
        {
            return PluginArtifactSpec.SourceType.valueOf(normalized.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception)
        {
            throw new MojoExecutionException(
                "Unsupported plugin sourceType '%s'. Supported values: %s"
                    .formatted(sourceType, List.of(PluginArtifactSpec.SourceType.values())),
                exception
            );
        }
    }

    private static String validateWorldName(@Nullable String worldName)
        throws MojoExecutionException
    {
        final String name = requireNonBlank(worldName, "lightkeeper.worlds.name");
        if (name.contains("/") || name.contains("\\") || name.equals(".") || name.contains(".."))
        {
            throw new MojoExecutionException(
                "Invalid world name '%s'. World names may not contain path separators, '.', or '..'."
                    .formatted(name)
            );
        }
        return name;
    }

    private static String normalizeWorldEnvironment(@Nullable String environment)
        throws MojoExecutionException
    {
        final String normalized = normalizeOptionalString(environment);
        if (normalized == null)
            return "NORMAL";

        final String upperCase = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("NORMAL", "NETHER", "THE_END").contains(upperCase))
        {
            throw new MojoExecutionException(
                "Unsupported world environment '%s'. Supported values: NORMAL, NETHER, THE_END."
                    .formatted(environment)
            );
        }
        return upperCase;
    }

    private static String normalizeWorldType(@Nullable String worldType)
        throws MojoExecutionException
    {
        final String normalized = normalizeOptionalString(worldType);
        if (normalized == null)
            return "NORMAL";

        final String upperCase = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("NORMAL", "FLAT").contains(upperCase))
        {
            throw new MojoExecutionException(
                "Unsupported worldType '%s'. Supported values: NORMAL, FLAT."
                    .formatted(worldType)
            );
        }
        return upperCase;
    }

    private static @Nullable String normalizeOptionalPluginFileName(@Nullable String fileName)
        throws MojoExecutionException
    {
        final String normalized = normalizeOptionalString(fileName);
        if (normalized == null)
            return null;
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".jar"))
            throw new MojoExecutionException("Configured renameTo '%s' must end with .jar.".formatted(normalized));
        if (normalized.contains("/") || normalized.contains("\\"))
        {
            throw new MojoExecutionException(
                "Configured renameTo '%s' may not contain path separators.".formatted(normalized)
            );
        }
        return normalized;
    }

    static String requireNonBlank(@Nullable String value, String fieldName)
        throws MojoExecutionException
    {
        final String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty())
            throw new MojoExecutionException("Missing required configuration value '%s'.".formatted(fieldName));
        return trimmed;
    }

    static @Nullable String normalizeOptionalString(@Nullable String value)
    {
        if (value == null)
            return null;
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
