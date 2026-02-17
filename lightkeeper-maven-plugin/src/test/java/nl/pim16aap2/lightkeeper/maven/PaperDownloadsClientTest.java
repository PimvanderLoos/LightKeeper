package nl.pim16aap2.lightkeeper.maven;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperDownloadsClientTest
{
    @Test
    void sortStableVersionsDescending_shouldSortStableSemanticVersions()
    {
        // setup
        final Set<String> versions = new LinkedHashSet<>(List.of(
            "1.21.9",
            "1.21.11",
            "1.20.6",
            "1.21.10",
            "1.21.11-rc1",
            "latest"
        ));

        // execute
        final List<String> sortedVersions = PaperDownloadsClient.sortStableVersionsDescending(versions);

        // verify
        assertThat(sortedVersions)
            .containsExactly("1.21.11", "1.21.10", "1.21.9", "1.20.6");
    }

    @Test
    void sortStableVersionsDescending_shouldIgnoreNonNumericEntriesAndTrimWhitespace()
    {
        // setup
        final Set<String> versions = new LinkedHashSet<>(List.of(
            " 1.21.2 ",
            "1.21.1",
            "dev-build",
            "1.19",
            " 1.21.0-pre1"
        ));

        // execute
        final List<String> sortedVersions = PaperDownloadsClient.sortStableVersionsDescending(versions);

        // verify
        assertThat(sortedVersions)
            .containsExactly("1.21.2", "1.21.1", "1.19");
    }

    @Test
    void resolveBuild_shouldResolveExplicitStableVersion()
        throws Exception
    {
        // setup
        final URI buildsUri = URI.create("https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds");
        final PaperDownloadsClient client = createClient(Map.of(
            buildsUri,
            """
                [
                  {
                    "id": 116,
                    "channel": "STABLE",
                    "downloads": {
                      "server:default": {
                        "name": "paper-1.21.11-116.jar",
                        "url": "https://fill.papermc.io/v1/objects/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/paper.jar",
                        "checksums": {
                          "sha256": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                        }
                      }
                    }
                  }
                ]
                """
        ));

        // execute
        final PaperBuildMetadata result = client.resolveBuild("1.21.11");

        // verify
        assertThat(result.minecraftVersion()).isEqualTo("1.21.11");
        assertThat(result.buildId()).isEqualTo(116L);
        assertThat(result.sha256()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Test
    void resolveBuild_shouldResolveLatestSupportedVersion()
        throws Exception
    {
        // setup
        final URI projectUri = URI.create("https://fill.papermc.io/v3/projects/paper");
        final URI buildsLatestUri = URI.create("https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds");
        final PaperDownloadsClient client = createClient(Map.of(
            projectUri,
            """
                {
                  "versions": {
                    "stable": ["1.21.11", "1.21.10", "1.21.11-rc1"]
                  }
                }
                """,
            buildsLatestUri,
            """
                [
                  {
                    "id": 116,
                    "channel": "STABLE",
                    "downloads": {
                      "server:default": {
                        "name": "paper.jar",
                        "url": "https://fill.papermc.io/v1/objects/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/paper.jar",
                        "checksums": {
                          "sha256": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
                        }
                      }
                    }
                  }
                ]
                """
        ));

        // execute
        final PaperBuildMetadata result = client.resolveBuild("latest-supported");

        // verify
        assertThat(result.minecraftVersion()).isEqualTo("1.21.11");
        assertThat(result.buildId()).isEqualTo(116L);
    }

    @Test
    void resolveBuild_shouldDeriveChecksumFromDownloadUrlWhenChecksumsAreMissing()
        throws Exception
    {
        // setup
        final URI buildsUri = URI.create("https://fill.papermc.io/v3/projects/paper/versions/1.21.10/builds");
        final PaperDownloadsClient client = createClient(Map.of(
            buildsUri,
            """
                [
                  {
                    "id": 115,
                    "channel": "STABLE",
                    "downloads": {
                      "server:default": {
                        "name": "paper.jar",
                        "url": "https://fill.papermc.io/v1/objects/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc/paper.jar"
                      }
                    }
                  }
                ]
                """
        ));

        // execute
        final PaperBuildMetadata result = client.resolveBuild("1.21.10");

        // verify
        assertThat(result.sha256()).isEqualTo("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
    }

    @Test
    void resolveBuild_shouldThrowExceptionWhenBuildIsMissing()
        throws Exception
    {
        // setup
        final URI buildsUri = URI.create("https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds");
        final PaperDownloadsClient client = createClient(Map.of(
            buildsUri,
            """
                [
                  {
                    "id": 116,
                    "channel": "EXPERIMENTAL",
                    "downloads": {}
                  }
                ]
                """
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolveBuild("1.21.11"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Could not find a stable Paper build");
    }

    @Test
    void resolveBuild_shouldThrowExceptionWhenLatestSupportedCannotBeResolved()
        throws Exception
    {
        // setup
        final URI projectUri = URI.create("https://fill.papermc.io/v3/projects/paper");
        final URI buildsUri = URI.create("https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds");
        final PaperDownloadsClient client = createClient(Map.of(
            projectUri,
            """
                {
                  "versions": {
                    "stable": ["1.21.11"]
                  }
                }
                """,
            buildsUri,
            """
                [
                  {
                    "id": 116,
                    "channel": "STABLE",
                    "downloads": {
                      "server:default": {
                        "name": "paper.jar",
                        "url": "https://example.com/nohash/paper.jar"
                      }
                    }
                  }
                ]
                """
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolveBuild("latest-supported"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Unable to resolve SHA-256 checksum");
    }

    @Test
    void resolveBuild_shouldThrowExceptionWhenResponseStatusIsNotSuccessful()
        throws Exception
    {
        // setup
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("{\"message\":\"fail\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        final PaperDownloadsClient client = createClient(httpClient);

        // execute + verify
        assertThatThrownBy(() -> client.resolveBuild("1.21.11"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("failed with status");
    }

    @Test
    void resolveBuild_shouldThrowExceptionWhenApiReturnsOkFalse()
        throws Exception
    {
        // setup
        final URI buildsUri = URI.create("https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds");
        final PaperDownloadsClient client = createClient(Map.of(
            buildsUri,
            "{\"ok\":false,\"message\":\"denied\"}"
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolveBuild("1.21.11"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("denied");
    }

    @Test
    void resolveBuild_shouldThrowExceptionWhenJsonIsInvalid()
        throws Exception
    {
        // setup
        final URI buildsUri = URI.create("https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds");
        final PaperDownloadsClient client = createClient(Map.of(
            buildsUri,
            "{invalid-json"
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolveBuild("1.21.11"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Failed to parse Fill API response");
    }

    @Test
    void constructor_shouldThrowExceptionWhenUserAgentIsBlank()
    {
        // execute + verify
        assertThatThrownBy(() -> new PaperDownloadsClient(new SystemStreamLog(), "  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User-Agent");
    }

    private static PaperDownloadsClient createClient(Map<URI, String> jsonByUri)
        throws Exception
    {
        final HttpClient httpClient = mock(HttpClient.class);
        for (Map.Entry<URI, String> entry : jsonByUri.entrySet())
        {
            final HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(entry.getValue());
            when(httpClient.send(
                argThat(request -> request != null && request.uri().equals(entry.getKey())),
                any(HttpResponse.BodyHandler.class)
            )).thenReturn(response);
        }
        return createClient(httpClient);
    }

    @SuppressWarnings("unchecked")
    private static PaperDownloadsClient createClient(HttpClient httpClient)
    {
        final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new PaperDownloadsClient(new SystemStreamLog(), "LightKeeper/Tests", httpClient, objectMapper);
    }
}
