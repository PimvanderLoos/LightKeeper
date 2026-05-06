package nl.pim16aap2.lightkeeper.maven.provisioning;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

/**
 * Validated plugin artifact provisioning input.
 *
 * @param sourceType
 *     Source type.
 * @param path
 *     Local path for {@code PATH} sources.
 * @param groupId
 *     Maven group id for {@code MAVEN} sources.
 * @param artifactId
 *     Maven artifact id for {@code MAVEN} sources.
 * @param version
 *     Maven version for {@code MAVEN} sources.
 * @param classifier
 *     Optional Maven classifier.
 * @param type
 *     Maven artifact type.
 * @param includeTransitive
 *     Whether transitive dependencies should be copied.
 * @param renameTo
 *     Optional output filename override.
 * @param uri
 *     Direct artifact URI for {@code URL} sources.
 * @param sha256
 *     Required SHA-256 checksum for {@code URL} sources.
 * @param modrinthProject
 *     Modrinth project slug or ID for {@code MODRINTH} sources.
 * @param modrinthVersion
 *     Modrinth version number for {@code MODRINTH} sources.
 * @param modrinthVersionId
 *     Modrinth version ID for {@code MODRINTH} sources.
 * @param modrinthLoader
 *     Modrinth loader to select. Defaults to Bukkit during config resolution.
 * @param modrinthGameVersion
 *     Optional Minecraft version filter for Modrinth version selection.
 */
public record PluginArtifactSpec(
    SourceType sourceType,
    @Nullable Path path,
    @Nullable String groupId,
    @Nullable String artifactId,
    @Nullable String version,
    @Nullable String classifier,
    String type,
    boolean includeTransitive,
    @Nullable String renameTo,
    @Nullable URI uri,
    @Nullable String sha256,
    @Nullable String modrinthProject,
    @Nullable String modrinthVersion,
    @Nullable String modrinthVersionId,
    @Nullable String modrinthLoader,
    @Nullable String modrinthGameVersion
)
{
    public PluginArtifactSpec(
        SourceType sourceType,
        @Nullable Path path,
        @Nullable String groupId,
        @Nullable String artifactId,
        @Nullable String version,
        @Nullable String classifier,
        String type,
        boolean includeTransitive,
        @Nullable String renameTo)
    {
        this(
            sourceType,
            path,
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
        );
    }

    /**
     * Plugin artifact source type.
     */
    public enum SourceType
    {
        PATH,
        MAVEN,
        MODRINTH,
        URL
    }
}
