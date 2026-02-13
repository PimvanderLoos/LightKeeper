package nl.pim16aap2.lightkeeper.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves BuildTools metadata for Spigot server preparation.
 */
public final class SpigotDownloadsClient
{
    private static final String LATEST_SUPPORTED = "latest-supported";
    private static final URI BUILDTOOLS_URI = URI.create(
        "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"
    );
    private static final String BUILDTOOLS_IDENTITY = "spigot-buildtools-lastSuccessfulBuild-target";

    private final Log log;
    private final PaperDownloadsClient paperDownloadsClient;

    /**
     * Creates a resolver for Spigot BuildTools metadata.
     *
     * @param log
     *     Maven log for diagnostics.
     * @param paperDownloadsClient
     *     Paper metadata resolver used only for {@code latest-supported} version resolution.
     */
    public SpigotDownloadsClient(Log log, PaperDownloadsClient paperDownloadsClient)
    {
        this.log = Objects.requireNonNull(log, "log may not be null.");
        this.paperDownloadsClient =
            Objects.requireNonNull(paperDownloadsClient, "paperDownloadsClient may not be null.");
    }

    /**
     * Resolves the Spigot build metadata for a requested version.
     *
     * @param requestedVersion
     *     Requested Minecraft version, or {@code latest-supported}.
     * @return Resolved metadata for BuildTools.
     * @throws MojoExecutionException
     *     When version resolution fails.
     */
    public SpigotBuildMetadata resolveBuild(String requestedVersion)
        throws MojoExecutionException
    {
        final String normalizedRequestedVersion = normalizeRequestedVersion(requestedVersion);
        final String resolvedVersion;
        if (LATEST_SUPPORTED.equalsIgnoreCase(normalizedRequestedVersion))
        {
            final PaperBuildMetadata paperBuildMetadata = paperDownloadsClient.resolveBuild(LATEST_SUPPORTED);
            resolvedVersion = paperBuildMetadata.minecraftVersion();
            log.info(
                "Resolved Spigot latest-supported version to Minecraft %s via Paper metadata."
                    .formatted(resolvedVersion)
            );
        }
        else
        {
            resolvedVersion = normalizedRequestedVersion;
        }

        return new SpigotBuildMetadata(
            resolvedVersion,
            BUILDTOOLS_URI,
            BUILDTOOLS_IDENTITY
        );
    }

    private static String normalizeRequestedVersion(String requestedVersion)
    {
        final String normalizedRequestedVersion = Objects.requireNonNull(
            requestedVersion,
            "requestedVersion may not be null."
        )
            .trim();
        if (normalizedRequestedVersion.isEmpty())
            throw new IllegalArgumentException("requestedVersion may not be blank.");
        return normalizedRequestedVersion.toLowerCase(Locale.ROOT);
    }
}
