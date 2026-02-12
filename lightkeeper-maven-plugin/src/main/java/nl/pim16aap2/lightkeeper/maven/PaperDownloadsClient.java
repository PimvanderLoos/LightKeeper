package nl.pim16aap2.lightkeeper.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Small client for PaperMC Fill v3.
 */
public final class PaperDownloadsClient
{
    private static final URI PROJECT_URI = URI.create("https://fill.papermc.io/v3/projects/paper");
    private static final String STABLE_CHANNEL = "STABLE";
    private static final String SERVER_DOWNLOAD_KEY = "server:default";
    private static final String LATEST_SUPPORTED = "latest-supported";
    private static final Pattern URL_HASH_PATTERN =
        Pattern.compile(".*/v1/objects/(?<hash>[a-fA-F0-9]{64})/.*");

    private final Log log;
    private final String userAgent;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PaperDownloadsClient(Log log, String userAgent)
    {
        this.log = Objects.requireNonNull(log, "log");
        this.userAgent = validateUserAgent(userAgent);
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public PaperBuildMetadata resolveBuild(String requestedVersion)
        throws MojoExecutionException
    {
        if (requestedVersion.equalsIgnoreCase(LATEST_SUPPORTED))
            return resolveLatestSupportedBuild();

        return resolveStableBuildForVersion(requestedVersion)
            .orElseThrow(() -> new MojoExecutionException(
                "Could not find a stable Paper build for version '%s'.".formatted(requestedVersion))
            );
    }

    private PaperBuildMetadata resolveLatestSupportedBuild()
        throws MojoExecutionException
    {
        final ProjectResponse projectResponse = fetchProject();
        final Set<String> versions = flattenVersions(projectResponse.versions());

        for (String version : versions)
        {
            if (version.contains("-"))
                continue;

            final Optional<PaperBuildMetadata> buildMetadata = resolveStableBuildForVersion(version);
            if (buildMetadata.isPresent())
                return buildMetadata.get();
        }

        throw new MojoExecutionException("Unable to resolve latest-supported Paper build.");
    }

    private Optional<PaperBuildMetadata> resolveStableBuildForVersion(String minecraftVersion)
        throws MojoExecutionException
    {
        final URI buildsUri = URI.create("%s/versions/%s/builds".formatted(PROJECT_URI, minecraftVersion));
        final JsonNode root = fetchJson(buildsUri);
        if (!root.isArray())
            throw new MojoExecutionException(
                "Expected Fill builds endpoint to return an array for version '%s'."
                    .formatted(minecraftVersion)
            );

        final List<BuildResponse> buildResponses = parseBuildResponses(root);
        for (BuildResponse buildResponse : buildResponses)
        {
            if (!STABLE_CHANNEL.equalsIgnoreCase(buildResponse.channel()))
                continue;

            final DownloadResponse downloadResponse = buildResponse.downloads().get(SERVER_DOWNLOAD_KEY);
            if (downloadResponse == null || downloadResponse.url() == null || downloadResponse.url().isBlank())
                continue;

            final URI downloadUri = URI.create(downloadResponse.url());
            final String sha256 = resolveSha256(downloadResponse, downloadUri)
                .orElseThrow(() -> new MojoExecutionException(
                    "Unable to resolve SHA-256 checksum for Paper build '%d' (%s)."
                        .formatted(buildResponse.id(), minecraftVersion)
                ));

            log.info(
                "Resolved stable Paper build %d for version %s."
                    .formatted(buildResponse.id(), minecraftVersion)
            );

            return Optional.of(
                new PaperBuildMetadata(
                    minecraftVersion,
                    buildResponse.id(),
                    downloadUri,
                    sha256.toLowerCase(Locale.ROOT)
                )
            );
        }

        return Optional.empty();
    }

    private Optional<String> resolveSha256(DownloadResponse downloadResponse, URI downloadUri)
    {
        if (downloadResponse.checksums() != null && downloadResponse.checksums().sha256() != null)
            return Optional.of(downloadResponse.checksums().sha256());

        final var matcher = URL_HASH_PATTERN.matcher(downloadUri.toString());
        if (matcher.matches())
            return Optional.of(matcher.group("hash"));

        return Optional.empty();
    }

    private ProjectResponse fetchProject()
        throws MojoExecutionException
    {
        final JsonNode root = fetchJson(PROJECT_URI);

        try
        {
            return objectMapper.treeToValue(root, ProjectResponse.class);
        }
        catch (JsonProcessingException exception)
        {
            throw new MojoExecutionException("Failed to parse project metadata from Fill response.", exception);
        }
    }

    private List<BuildResponse> parseBuildResponses(JsonNode root)
        throws MojoExecutionException
    {
        try
        {
            return objectMapper.readValue(root.traverse(), new TypeReference<>()
            {
            });
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException("Failed to parse Fill builds response.", exception);
        }
    }

    private JsonNode fetchJson(URI uri)
        throws MojoExecutionException
    {
        final HttpRequest request = HttpRequest.newBuilder(uri)
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
        catch (IOException | InterruptedException exception)
        {
            if (exception instanceof InterruptedException)
                Thread.currentThread().interrupt();
            throw new MojoExecutionException("Failed to query Fill API at '%s'.".formatted(uri), exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new MojoExecutionException(
                "Fill API request to '%s' failed with status %d."
                    .formatted(uri, response.statusCode())
            );

        try
        {
            final JsonNode root = objectMapper.readTree(response.body());
            if (root.isObject() && root.has("ok") && !root.path("ok").asBoolean(true))
                throw new MojoExecutionException(
                    "Fill API request to '%s' failed: %s".formatted(uri, root.path("message").asText("unknown error"))
                );
            return root;
        }
        catch (JsonProcessingException exception)
        {
            throw new MojoExecutionException("Failed to parse Fill API response from '%s'.".formatted(uri), exception);
        }
    }

    private Set<String> flattenVersions(Map<String, List<String>> versionsByGroup)
    {
        final Set<String> versions = new LinkedHashSet<>();
        for (List<String> versionsInGroup : versionsByGroup.values())
            versions.addAll(versionsInGroup);
        return versions;
    }

    private static String validateUserAgent(String userAgent)
    {
        if (userAgent == null || userAgent.isBlank())
            throw new IllegalArgumentException("A non-empty Fill API User-Agent is required.");
        return userAgent;
    }

    private record ProjectResponse(
        Map<String, List<String>> versions
    )
    {
    }

    private record BuildResponse(
        long id,
        String channel,
        Map<String, DownloadResponse> downloads
    )
    {
    }

    private record DownloadResponse(
        String name,
        String url,
        Checksums checksums
    )
    {
    }

    private record Checksums(
        String sha256
    )
    {
    }
}
