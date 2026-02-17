package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Raw plugin configuration for one world preload input.
 */
public final class PrepareServerWorldInputConfig
{
    @Parameter(required = true)
    @Nullable
    private String name;

    @Parameter(required = true)
    @Nullable
    private String sourceType;

    @Parameter(required = true)
    @Nullable
    private Path sourcePath;

    @Parameter
    @Nullable
    private Boolean overwrite;

    @Parameter
    @Nullable
    private Boolean loadOnStartup;

    @Parameter
    @Nullable
    private String environment;

    @Parameter
    @Nullable
    private String worldType;

    @Parameter
    @Nullable
    private Long seed;

    @Nullable String name()
    {
        return name;
    }

    @Nullable String sourceType()
    {
        return sourceType;
    }

    @Nullable Path sourcePath()
    {
        return sourcePath;
    }

    @Nullable Boolean overwrite()
    {
        return overwrite;
    }

    @Nullable Boolean loadOnStartup()
    {
        return loadOnStartup;
    }

    @Nullable String environment()
    {
        return environment;
    }

    @Nullable String worldType()
    {
        return worldType;
    }

    @Nullable Long seed()
    {
        return seed;
    }
}
