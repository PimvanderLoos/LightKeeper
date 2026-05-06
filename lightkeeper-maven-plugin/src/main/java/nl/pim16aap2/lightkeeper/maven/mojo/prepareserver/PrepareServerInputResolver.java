package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import org.apache.maven.plugin.MojoExecutionException;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Resolves and validates world/plugin input sections for {@code prepare-server}.
 */
final class PrepareServerInputResolver
{
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[a-fA-F0-9]{64}");

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
                    renameTo,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ));
                continue;
            }

            if (sourceType == PluginArtifactSpec.SourceType.URL)
            {
                final URI url = requireHttpUri(pluginArtifactConfig.url(), "lightkeeper.plugins.url");
                final String sha256 = requireSha256(pluginArtifactConfig.sha256(), "lightkeeper.plugins.sha256");
                final String outputFileName = renameTo == null
                    ? validatePluginFileName(extractFileName(url), "URL filename")
                    : renameTo;
                specs.add(new PluginArtifactSpec(
                    sourceType,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "jar",
                    false,
                    outputFileName,
                    url,
                    sha256,
                    null,
                    null,
                    null,
                    null,
                    null
                ));
                continue;
            }

            if (sourceType == PluginArtifactSpec.SourceType.MODRINTH)
            {
                final @Nullable String versionId = normalizeOptionalString(pluginArtifactConfig.modrinthVersionId());
                final @Nullable String project = normalizeOptionalString(pluginArtifactConfig.modrinthProject());
                final @Nullable String version = normalizeOptionalString(pluginArtifactConfig.modrinthVersion());
                if (versionId == null && (project == null || version == null))
                {
                    throw new MojoExecutionException(
                        "Configured Modrinth plugin requires either 'lightkeeper.plugins.modrinthVersionId' or both " +
                            "'lightkeeper.plugins.modrinthProject' and 'lightkeeper.plugins.modrinthVersion'."
                    );
                }
                if (versionId != null && (project != null || version != null))
                {
                    throw new MojoExecutionException(
                        "Configured Modrinth plugin may not combine modrinthVersionId with modrinthProject or " +
                            "modrinthVersion."
                    );
                }

                specs.add(new PluginArtifactSpec(
                    sourceType,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "jar",
                    false,
                    renameTo,
                    null,
                    null,
                    project,
                    version,
                    versionId,
                    normalizeOptionalString(pluginArtifactConfig.modrinthLoader()) == null
                        ? "bukkit"
                        : normalizeOptionalString(pluginArtifactConfig.modrinthLoader()),
                    normalizeOptionalString(pluginArtifactConfig.modrinthGameVersion())
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
                renameTo,
                null,
                null,
                null,
                null,
                null,
                null,
                null
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

    private static String validatePluginFileName(String fileName, String description)
        throws MojoExecutionException
    {
        final String normalized = requireNonBlank(fileName, description);
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".jar"))
        {
            throw new MojoExecutionException(
                "Configured plugin filename '%s' must end with .jar.".formatted(normalized)
            );
        }
        if (normalized.contains("/") || normalized.contains("\\") ||
            normalized.equals(".") || normalized.contains(".."))
        {
            throw new MojoExecutionException(
                "Configured plugin filename '%s' may not contain path separators, '.', or '..'."
                    .formatted(normalized)
            );
        }
        return normalized;
    }

    private static URI requireHttpUri(@Nullable URI value, String fieldName)
        throws MojoExecutionException
    {
        if (value == null)
            throw new MojoExecutionException("Missing required configuration value '%s'.".formatted(fieldName));
        final String scheme = value.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
            throw new MojoExecutionException("Configured URL '%s' must use http or https.".formatted(value));
        return value;
    }

    private static String requireSha256(@Nullable String value, String fieldName)
        throws MojoExecutionException
    {
        final String normalized = requireNonBlank(value, fieldName).toLowerCase(Locale.ROOT);
        if (!SHA_256_PATTERN.matcher(normalized).matches())
            throw new MojoExecutionException("Configured SHA-256 value '%s' must be 64 hexadecimal characters."
                .formatted(value));
        return normalized;
    }

    private static String extractFileName(URI uri)
        throws MojoExecutionException
    {
        final String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/"))
            throw new MojoExecutionException("Configured URL '%s' does not include an output filename.".formatted(uri));
        return path.substring(path.lastIndexOf('/') + 1);
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
