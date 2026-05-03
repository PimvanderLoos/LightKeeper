package nl.pim16aap2.lightkeeper.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves BuildTools metadata for Spigot server preparation.
 */
public final class SpigotDownloadsClient
{
    private static final String LATEST_SUPPORTED = "latest-supported";
    private static final String BUILDTOOLS_IDENTITY_PREFIX = "spigot-buildtools-build-";
    private static final URI DEFAULT_BUILDTOOLS_URI = URI.create(
        "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"
    );
    private static final URI DEFAULT_BUILDTOOLS_METADATA_URI = URI.create(
        "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/api/json"
    );
    private static final Pattern BUILDTOOLS_BUILD_URL_PATTERN =
        Pattern.compile(".*/job/BuildTools/(?<buildNumber>\\d+)/.*");

    private final Log log;
    private final String userAgent;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI buildToolsUri;
    private final URI buildToolsMetadataUri;

    /**
     * Creates a resolver for Spigot BuildTools metadata.
     *
     * @param log
     *     Maven log for diagnostics.
     * @param userAgent
     *     HTTP user-agent used for BuildTools metadata requests.
     */
    public SpigotDownloadsClient(Log log, String userAgent)
    {
        this(
            log,
            userAgent,
            DEFAULT_BUILDTOOLS_URI,
            DEFAULT_BUILDTOOLS_METADATA_URI
        );
    }

    /**
     * Creates a resolver for Spigot BuildTools metadata with explicit endpoint overrides.
     *
     * @param log
     *     Maven log for diagnostics.
     * @param userAgent
     *     HTTP user-agent used for BuildTools metadata requests.
     * @param buildToolsUri
     *     Download URI for BuildTools.
     * @param buildToolsMetadataUri
     *     Metadata URI for the currently active BuildTools build.
     */
    SpigotDownloadsClient(
        Log log,
        String userAgent,
        URI buildToolsUri,
        URI buildToolsMetadataUri)
    {
        this.log = Objects.requireNonNull(log, "log may not be null.");
        this.userAgent = validateUserAgent(userAgent);
        this.buildToolsUri = Objects.requireNonNull(buildToolsUri, "buildToolsUri may not be null.");
        this.buildToolsMetadataUri =
            Objects.requireNonNull(buildToolsMetadataUri, "buildToolsMetadataUri may not be null.");
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
        final String resolvedVersion = resolveSupportedMinecraftVersion(requestedVersion);

        final String buildToolsIdentity = resolveBuildToolsIdentity();
        return new SpigotBuildMetadata(
            resolvedVersion,
            buildToolsUri,
            buildToolsIdentity
        );
    }

    private String resolveBuildToolsIdentity()
        throws MojoExecutionException
    {
        final JsonNode buildMetadata = fetchBuildToolsMetadata();
        final long buildNumber = resolveBuildNumber(buildMetadata)
            .orElseThrow(() -> new MojoExecutionException(
                "Failed to resolve BuildTools build number from metadata at '%s'."
                    .formatted(buildToolsMetadataUri)
            ));
        return BUILDTOOLS_IDENTITY_PREFIX + buildNumber;
    }

    private JsonNode fetchBuildToolsMetadata()
        throws MojoExecutionException
    {
        final HttpRequest request = HttpRequest.newBuilder(buildToolsMetadataUri)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        final HttpResponse<String> response;
        try
        {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException(
                "Failed to query BuildTools metadata at '%s'.".formatted(buildToolsMetadataUri),
                exception
            );
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to query BuildTools metadata at '%s'.".formatted(buildToolsMetadataUri),
                exception
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new MojoExecutionException(
                "BuildTools metadata request to '%s' failed with status %d."
                    .formatted(buildToolsMetadataUri, response.statusCode())
            );
        }

        try
        {
            return objectMapper.readTree(response.body());
        }
        catch (JsonProcessingException exception)
        {
            throw new MojoExecutionException(
                "Failed to parse BuildTools metadata response from '%s'."
                    .formatted(buildToolsMetadataUri),
                exception
            );
        }
    }

    private static Optional<Long> resolveBuildNumber(JsonNode buildMetadata)
    {
        final long explicitBuildNumber = buildMetadata.path("number").asLong(-1L);
        if (explicitBuildNumber > 0L)
            return Optional.of(explicitBuildNumber);

        final String buildUrl = buildMetadata.path("url").asText("");
        final var matcher = BUILDTOOLS_BUILD_URL_PATTERN.matcher(buildUrl);
        if (!matcher.matches())
            return Optional.empty();

        return Optional.of(Long.parseLong(matcher.group("buildNumber")));
    }

    private String resolveSupportedMinecraftVersion(String requestedVersion)
        throws MojoExecutionException
    {
        final String normalizedRequestedVersion = Objects.requireNonNull(
            requestedVersion,
            "requestedVersion may not be null."
        )
            .trim();
        if (normalizedRequestedVersion.isEmpty())
            throw new IllegalArgumentException("requestedVersion may not be blank.");

        if (LATEST_SUPPORTED.equalsIgnoreCase(normalizedRequestedVersion))
        {
            log.info(
                "Resolved Spigot latest-supported version to Minecraft %s."
                    .formatted(RuntimeProtocol.SUPPORTED_MINECRAFT_VERSION)
            );
            return RuntimeProtocol.SUPPORTED_MINECRAFT_VERSION;
        }

        if (normalizedRequestedVersion.equals(RuntimeProtocol.SUPPORTED_MINECRAFT_VERSION))
            return normalizedRequestedVersion;

        throw new MojoExecutionException(
            "Unsupported Spigot version '%s'. This LightKeeper build supports Minecraft version '%s'."
                .formatted(normalizedRequestedVersion, RuntimeProtocol.SUPPORTED_MINECRAFT_VERSION)
        );
    }

    private static String validateUserAgent(String userAgent)
    {
        if (userAgent == null || userAgent.isBlank())
            throw new IllegalArgumentException("A non-empty BuildTools User-Agent is required.");
        return userAgent;
    }
}
