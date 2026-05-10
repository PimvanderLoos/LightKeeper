package nl.pim16aap2.lightkeeper.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Small client for Modrinth v2 plugin metadata.
 */
public final class ModrinthDownloadsClient
{
    private static final URI API_BASE_URI = URI.create("https://api.modrinth.com/v2");

    private final Log log;
    private final String userAgent;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a Modrinth downloads client.
     *
     * @param log
     *     Maven log sink.
     * @param userAgent
     *     HTTP user-agent header value.
     */
    public ModrinthDownloadsClient(Log log, String userAgent)
    {
        this(
            log,
            userAgent,
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build(),
            new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        );
    }

    public ModrinthDownloadsClient(Log log, String userAgent, HttpClient httpClient)
    {
        this(
            log,
            userAgent,
            httpClient,
            new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        );
    }

    ModrinthDownloadsClient(Log log, String userAgent, HttpClient httpClient, ObjectMapper objectMapper)
    {
        this.log = Objects.requireNonNull(log, "log");
        this.userAgent = validateUserAgent(userAgent);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Resolves an exact Modrinth version and selects one jar file compatible with the configured loader.
     *
     * @param spec
     *     Validated Modrinth plugin spec.
     * @return Selected Modrinth file metadata.
     * @throws MojoExecutionException
     *     When metadata cannot be resolved unambiguously.
     */
    public ModrinthPluginMetadata resolvePluginFile(PluginArtifactSpec spec)
        throws MojoExecutionException
    {
        final VersionResponse version = spec.modrinthVersionId() == null
            ? resolveByProjectAndVersionNumber(spec)
            : fetchVersion(Objects.requireNonNull(spec.modrinthVersionId()));
        validateVersionCompatibility(
            version,
            Objects.requireNonNull(spec.modrinthLoader()),
            spec.modrinthGameVersion()
        );
        final FileResponse file = selectFile(version, Objects.requireNonNull(spec.modrinthLoader()));

        log.info("LK_PLUGIN: Resolved Modrinth plugin '%s' version '%s' file '%s'."
            .formatted(version.projectId(), version.versionNumber(), file.filename()));

        return new ModrinthPluginMetadata(
            version.id(),
            version.versionNumber(),
            file.filename(),
            requireDownloadUri(file, version),
            requireSha512(file)
        );
    }

    private VersionResponse resolveByProjectAndVersionNumber(PluginArtifactSpec spec)
        throws MojoExecutionException
    {
        final String project = Objects.requireNonNull(spec.modrinthProject());
        final String loader = Objects.requireNonNull(spec.modrinthLoader());
        final String query = query(
            "loaders",
            "[\"%s\"]".formatted(loader),
            "include_changelog",
            "false"
        ) + (spec.modrinthGameVersion() == null
            ? ""
            : "&" + query("game_versions", "[\"%s\"]".formatted(spec.modrinthGameVersion())));
        final URI uri = API_BASE_URI.resolve("/v2/project/%s/version?%s".formatted(encodePath(project), query));
        final List<VersionResponse> versions = fetchVersions(uri);
        final List<VersionResponse> matches = versions.stream()
            .filter(version -> Objects.requireNonNull(spec.modrinthVersion()).equals(version.versionNumber()))
            .toList();
        if (matches.isEmpty())
        {
            throw new MojoExecutionException(
                "Could not find Modrinth version '%s' for project '%s' with loader '%s'."
                    .formatted(spec.modrinthVersion(), project, loader)
            );
        }
        if (matches.size() > 1)
        {
            throw new MojoExecutionException(
                "Modrinth version '%s' for project '%s' resolved to %d entries; use modrinthVersionId."
                    .formatted(spec.modrinthVersion(), project, matches.size())
            );
        }
        return matches.getFirst();
    }

    private VersionResponse fetchVersion(String versionId)
        throws MojoExecutionException
    {
        final URI uri = API_BASE_URI.resolve("/v2/version/%s".formatted(encodePath(versionId)));
        return parseVersion(fetchJson(uri), uri);
    }

    private List<VersionResponse> fetchVersions(URI uri)
        throws MojoExecutionException
    {
        try
        {
            return objectMapper.readValue(fetchJson(uri).traverse(), new TypeReference<>()
            {
            });
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException("Failed to parse Modrinth versions response from '%s'.".formatted(uri),
                exception);
        }
    }

    private VersionResponse parseVersion(JsonNode root, URI uri)
        throws MojoExecutionException
    {
        try
        {
            return objectMapper.treeToValue(root, VersionResponse.class);
        }
        catch (JsonProcessingException exception)
        {
            throw new MojoExecutionException("Failed to parse Modrinth version response from '%s'.".formatted(uri),
                exception);
        }
    }

    private void validateVersionCompatibility(
        VersionResponse version,
        String loader,
        @Nullable String gameVersion)
        throws MojoExecutionException
    {
        if (!containsIgnoreCase(nonNullList(version.loaders()), loader))
        {
            throw new MojoExecutionException(
                "Modrinth version '%s' is not compatible with loader '%s'."
                    .formatted(version.id(), loader)
            );
        }
        if (gameVersion != null && !nonNullList(version.gameVersions()).contains(gameVersion))
        {
            throw new MojoExecutionException(
                "Modrinth version '%s' is not compatible with Minecraft version '%s'."
                    .formatted(version.id(), gameVersion)
            );
        }
    }

    private FileResponse selectFile(VersionResponse version, String loader)
        throws MojoExecutionException
    {
        final List<FileResponse> jarFiles = nonNullList(version.files()).stream()
            .filter(file -> file.filename() != null && file.filename().toLowerCase(Locale.ROOT).endsWith(".jar"))
            .filter(file -> file.fileType() == null || "unknown".equals(file.fileType()))
            .toList();
        if (jarFiles.isEmpty())
        {
            throw new MojoExecutionException(
                "Modrinth version '%s' has no installable jar file.".formatted(version.id())
            );
        }

        final List<FileResponse> primaryFiles = jarFiles.stream().filter(FileResponse::primary).toList();
        if (primaryFiles.size() == 1)
            return primaryFiles.getFirst();
        if (primaryFiles.size() > 1)
        {
            throw new MojoExecutionException(
                "Modrinth version '%s' has multiple primary jar files.".formatted(version.id())
            );
        }
        if (jarFiles.size() == 1)
            return jarFiles.getFirst();

        final List<FileResponse> requiredFiles = jarFiles.stream()
            .filter(file -> file.fileType() == null)
            .sorted(Comparator.comparing(FileResponse::filename))
            .toList();
        if (requiredFiles.size() == 1)
            return requiredFiles.getFirst();

        throw new MojoExecutionException(
            "Modrinth version '%s' has ambiguous jar files for loader '%s'; choose a version with one primary jar."
                .formatted(version.id(), loader)
        );
    }

    private static String requireSha512(FileResponse file)
        throws MojoExecutionException
    {
        final String sha512 = nonNullMap(file.hashes()).get("sha512");
        if (sha512 == null || sha512.isBlank())
            throw new MojoExecutionException("Modrinth file '%s' has no SHA-512 checksum.".formatted(file.filename()));
        return sha512.toLowerCase(Locale.ROOT);
    }

    private static URI requireDownloadUri(FileResponse file, VersionResponse version)
        throws MojoExecutionException
    {
        final String url = file.url();
        if (url == null || url.isBlank())
        {
            throw new MojoExecutionException(
                "Modrinth file '%s' in version '%s' has no download URL.".formatted(file.filename(), version.id())
            );
        }

        final URI uri;
        try
        {
            uri = URI.create(url);
        }
        catch (IllegalArgumentException exception)
        {
            throw new MojoExecutionException(
                "Modrinth file '%s' in version '%s' has invalid download URL '%s'."
                    .formatted(file.filename(), version.id(), url),
                exception
            );
        }

        final String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
        {
            throw new MojoExecutionException(
                "Modrinth file '%s' in version '%s' download URL '%s' must use http or https."
                    .formatted(file.filename(), version.id(), url)
            );
        }
        return uri;
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
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Failed to query Modrinth API at '%s'.".formatted(uri), exception);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException("Failed to query Modrinth API at '%s'.".formatted(uri), exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new MojoExecutionException(
                "Modrinth API request to '%s' failed with status %d.".formatted(uri, response.statusCode())
            );

        try
        {
            return objectMapper.readTree(response.body());
        }
        catch (JsonProcessingException exception)
        {
            throw new MojoExecutionException("Failed to parse Modrinth API response from '%s'.".formatted(uri),
                exception);
        }
    }

    private static boolean containsIgnoreCase(List<String> values, String expected)
    {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
    }

    private static <T> List<T> nonNullList(@Nullable List<T> value)
    {
        return value == null ? List.of() : value;
    }

    private static <K, V> Map<K, V> nonNullMap(@Nullable Map<K, V> value)
    {
        return value == null ? Map.of() : value;
    }

    private static String query(String... keyValues)
    {
        final StringBuilder ret = new StringBuilder();
        for (int idx = 0; idx < keyValues.length; idx += 2)
        {
            if (!ret.isEmpty())
                ret.append('&');
            ret.append(encodeQuery(keyValues[idx])).append('=').append(encodeQuery(keyValues[idx + 1]));
        }
        return ret.toString();
    }

    private static String encodePath(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeQuery(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String validateUserAgent(String userAgent)
    {
        if (userAgent == null || userAgent.isBlank())
            throw new IllegalArgumentException("A non-empty Modrinth API User-Agent is required.");
        return userAgent;
    }

    private record VersionResponse(
        String id,
        String projectId,
        String versionNumber,
        List<String> gameVersions,
        List<String> loaders,
        List<FileResponse> files
    )
    {
    }

    private record FileResponse(
        Map<String, String> hashes,
        String url,
        String filename,
        boolean primary,
        @Nullable String fileType
    )
    {
    }
}
