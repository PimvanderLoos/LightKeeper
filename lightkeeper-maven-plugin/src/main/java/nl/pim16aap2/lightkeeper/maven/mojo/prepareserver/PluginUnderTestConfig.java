package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Selects a packaged plugin from the current project, a declared dependency, or an explicit local file.
 * A classifier alone selects an attached artifact of the current project.
 */
@AllArgsConstructor
@NoArgsConstructor
public final class PluginUnderTestConfig
{
    /** Optional group that disambiguates a declared dependency's artifact ID. */
    @Parameter
    @Nullable
    private String groupId;

    /** Artifact ID of an existing declared dependency; omitted to select the current project. */
    @Parameter
    @Nullable
    private String artifactId;

    /** Optional classifier of the selected project's or dependency's packaged artifact. */
    @Parameter
    @Nullable
    private String classifier;

    /** Authoritative local JAR override, mutually exclusive with artifact coordinates. */
    @Parameter
    @Nullable
    private Path path;

    @Nullable String groupId()
    {
        return groupId;
    }

    @Nullable String artifactId()
    {
        return artifactId;
    }

    @Nullable String classifier()
    {
        return classifier;
    }

    @Nullable Path path()
    {
        return path;
    }
}
