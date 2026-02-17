package nl.pim16aap2.lightkeeper.maven;

import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Raw plugin configuration for one plugin artifact input.
 */
public final class PrepareServerPluginArtifactConfig
{
    @Parameter(required = true)
    @Nullable
    private String sourceType;

    @Parameter
    @Nullable
    private Path path;

    @Parameter
    @Nullable
    private String groupId;

    @Parameter
    @Nullable
    private String artifactId;

    @Parameter
    @Nullable
    private String version;

    @Parameter
    @Nullable
    private String classifier;

    @Parameter
    @Nullable
    private String type;

    @Parameter
    @Nullable
    private Boolean includeTransitive;

    @Parameter
    @Nullable
    private String renameTo;

    @Nullable String sourceType()
    {
        return sourceType;
    }

    @Nullable Path path()
    {
        return path;
    }

    @Nullable String groupId()
    {
        return groupId;
    }

    @Nullable String artifactId()
    {
        return artifactId;
    }

    @Nullable String version()
    {
        return version;
    }

    @Nullable String classifier()
    {
        return classifier;
    }

    @Nullable String type()
    {
        return type;
    }

    @Nullable Boolean includeTransitive()
    {
        return includeTransitive;
    }

    @Nullable String renameTo()
    {
        return renameTo;
    }
}
