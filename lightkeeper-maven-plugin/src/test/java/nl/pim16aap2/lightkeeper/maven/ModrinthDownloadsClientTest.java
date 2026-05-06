package nl.pim16aap2.lightkeeper.maven;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModrinthDownloadsClientTest
{
    @Test
    void resolvePluginFile_shouldResolveProjectVersionAndPrimaryJar()
        throws Exception
    {
        // setup
        final URI versionsUri = URI.create(
            "https://api.modrinth.com/v2/project/luckperms/version?loaders=%5B%22bukkit%22%5D&include_changelog=false"
        );
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionsUri,
            """
                [
                  {
                    "id": "version01",
                    "project_id": "project01",
                    "version_number": "v5.5.0-bukkit",
                    "game_versions": ["1.21.11"],
                    "loaders": ["bukkit"],
                    "files": [
                      {
                        "hashes": {
                          "sha512": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        },
                        "url": "https://cdn.modrinth.com/data/project01/versions/version01/LuckPerms-Bukkit.jar",
                        "filename": "LuckPerms-Bukkit.jar",
                        "primary": true
                      }
                    ]
                  }
                ]
                """
        ));

        // execute
        final ModrinthPluginMetadata metadata = client.resolvePluginFile(modrinthProjectSpec());

        // verify
        assertThat(metadata.versionId()).isEqualTo("version01");
        assertThat(metadata.versionNumber()).isEqualTo("v5.5.0-bukkit");
        assertThat(metadata.fileName()).isEqualTo("LuckPerms-Bukkit.jar");
        assertThat(metadata.sha512()).startsWith("aaaaaaaa");
    }

    @Test
    void resolvePluginFile_shouldRejectAmbiguousFiles()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            """
                {
                  "id": "version01",
                  "project_id": "project01",
                  "version_number": "v5.5.0-bukkit",
                  "game_versions": ["1.21.11"],
                  "loaders": ["bukkit"],
                  "files": [
                    {
                      "hashes": {"sha512": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                      "url": "https://cdn.modrinth.com/a.jar",
                      "filename": "a.jar",
                      "primary": false
                    },
                    {
                      "hashes": {"sha512": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},
                      "url": "https://cdn.modrinth.com/b.jar",
                      "filename": "b.jar",
                      "primary": false
                    }
                  ]
                }
                """
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthVersionIdSpec()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("ambiguous jar files");
    }

    private static PluginArtifactSpec modrinthProjectSpec()
    {
        return new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.MODRINTH,
            null,
            null,
            null,
            null,
            null,
            "jar",
            false,
            null,
            null,
            null,
            "luckperms",
            "v5.5.0-bukkit",
            null,
            "bukkit",
            null
        );
    }

    private static PluginArtifactSpec modrinthVersionIdSpec()
    {
        return new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.MODRINTH,
            null,
            null,
            null,
            null,
            null,
            "jar",
            false,
            null,
            null,
            null,
            null,
            null,
            "version01",
            "bukkit",
            null
        );
    }

    private static ModrinthDownloadsClient createClient(Map<URI, String> responses)
        throws Exception
    {
        final HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(
            argThat((HttpRequest request) -> responses.containsKey(request.uri())),
            any(HttpResponse.BodyHandler.class)
        )).thenAnswer(invocation -> {
            final HttpRequest request = invocation.getArgument(0);
            final HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(responses.get(request.uri()));
            return response;
        });
        return new ModrinthDownloadsClient(
            new SystemStreamLog(),
            "LightKeeper/Test",
            httpClient,
            new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        );
    }
}
