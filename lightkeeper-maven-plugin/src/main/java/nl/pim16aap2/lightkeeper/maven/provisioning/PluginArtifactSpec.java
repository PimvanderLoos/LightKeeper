package nl.pim16aap2.lightkeeper.maven.provisioning;

import org.jspecify.annotations.Nullable;

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
    @Nullable String renameTo
)
{
    /**
     * Plugin artifact source type.
     */
    public enum SourceType
    {
        PATH,
        MAVEN
    }
}
