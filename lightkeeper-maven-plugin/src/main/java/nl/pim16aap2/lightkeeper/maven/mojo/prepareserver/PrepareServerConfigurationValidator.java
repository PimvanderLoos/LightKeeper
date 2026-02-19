package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import org.apache.maven.plugin.MojoExecutionException;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Validates and normalizes top-level {@code prepare-server} configuration values.
 */
final class PrepareServerConfigurationValidator
{
    private final List<String> supportedServerTypes;

    PrepareServerConfigurationValidator(List<String> supportedServerTypes)
    {
        this.supportedServerTypes = List.copyOf(supportedServerTypes);
    }

    String normalizeServerType(@Nullable String configuredServerType)
    {
        return Objects.requireNonNull(configuredServerType, "serverType may not be null.")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    void validateConfiguration(
        @Nullable String configuredServerType,
        @Nullable String configuredUserAgent,
        @Nullable Path configuredAgentJarPath,
        int configuredServerStartMaxAttempts,
        int configuredJarCacheExpiryDays,
        int configuredBaseServerCacheExpiryDays,
        @Nullable String configuredExtraJvmArgs
    )
        throws MojoExecutionException
    {
        final String normalizedType = normalizeServerType(configuredServerType);
        if (!supportedServerTypes.contains(normalizedType))
        {
            throw new MojoExecutionException(
                "Unsupported server type '%s'. Supported types: %s"
                    .formatted(configuredServerType, supportedServerTypes)
            );
        }

        if (configuredUserAgent == null || configuredUserAgent.isBlank())
            throw new MojoExecutionException("A non-empty `lightkeeper.userAgent` value is required.");

        if (configuredAgentJarPath != null)
        {
            throw new MojoExecutionException(
                "Configuration parameter `lightkeeper.agentJarPath` is no longer supported. "
                    + "The runtime agent is provisioned internally by the LightKeeper Maven plugin."
            );
        }

        if (configuredServerStartMaxAttempts < 1)
            throw new MojoExecutionException("`lightkeeper.serverStartMaxAttempts` must be at least 1.");

        if (configuredJarCacheExpiryDays < 0)
            throw new MojoExecutionException("`lightkeeper.jarCacheExpiryDays` must be at least 0.");

        if (configuredBaseServerCacheExpiryDays < 0)
            throw new MojoExecutionException("`lightkeeper.baseServerCacheExpiryDays` must be at least 0.");

        PrepareServerRuntimeSupport.validateExtraJvmArgs(configuredExtraJvmArgs);
    }
}
