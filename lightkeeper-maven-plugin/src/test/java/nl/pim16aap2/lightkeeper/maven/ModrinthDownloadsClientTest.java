package nl.pim16aap2.lightkeeper.maven;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

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
        final ModrinthDownloadsClient client = createJsonClient(Map.of(
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
    void resolvePluginFile_shouldResolveVersionIdAndSingleJarWithoutPrimary()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            jsonResponse(
                200,
                """
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
                          "primary": false,
                          "file_type": "unknown"
                        }
                      ]
                    }
                    """
            )
        ));

        // execute
        final ModrinthPluginMetadata metadata = client.resolvePluginFile(modrinthVersionIdSpec());

        // verify
        assertThat(metadata.versionId()).isEqualTo("version01");
        assertThat(metadata.fileName()).isEqualTo("LuckPerms-Bukkit.jar");
        assertThat(metadata.downloadUri()).isEqualTo(URI.create(
            "https://cdn.modrinth.com/data/project01/versions/version01/LuckPerms-Bukkit.jar"
        ));
    }

    @Test
    void resolvePluginFile_shouldRejectMissingVersionWhenResolvingByProject()
        throws Exception
    {
        // setup
        final URI versionsUri = URI.create(
            "https://api.modrinth.com/v2/project/luckperms/version?loaders=%5B%22bukkit%22%5D&include_changelog=false"
        );
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionsUri,
            jsonResponse(200, "[]")
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthProjectSpec()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Could not find Modrinth version");
    }

    @Test
    void resolvePluginFile_shouldRejectAmbiguousProjectVersionMatches()
        throws Exception
    {
        // setup
        final URI versionsUri = URI.create(
            "https://api.modrinth.com/v2/project/luckperms/version?loaders=%5B%22bukkit%22%5D&include_changelog=false"
        );
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionsUri,
            jsonResponse(
                200,
                """
                    [
                      {
                        "id": "version01",
                        "project_id": "project01",
                        "version_number": "v5.5.0-bukkit",
                        "game_versions": ["1.21.11"],
                        "loaders": ["bukkit"],
                        "files": []
                      },
                      {
                        "id": "version02",
                        "project_id": "project01",
                        "version_number": "v5.5.0-bukkit",
                        "game_versions": ["1.21.11"],
                        "loaders": ["bukkit"],
                        "files": []
                      }
                    ]
                    """
            )
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthProjectSpec()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("resolved to 2 entries");
    }

    @Test
    void resolvePluginFile_shouldRejectIncompatibleLoader()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            jsonResponse(
                200,
                """
                    {
                      "id": "version01",
                      "project_id": "project01",
                      "version_number": "v5.5.0-bukkit",
                      "game_versions": ["1.21.11"],
                      "loaders": ["fabric"],
                      "files": []
                    }
                    """
            )
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthVersionIdSpec()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("not compatible with loader");
    }

    @Test
    void resolvePluginFile_shouldRejectIncompatibleGameVersion()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            jsonResponse(
                200,
                """
                    {
                      "id": "version01",
                      "project_id": "project01",
                      "version_number": "v5.5.0-bukkit",
                      "game_versions": ["1.21.10"],
                      "loaders": ["bukkit"],
                      "files": []
                    }
                    """
            )
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthVersionIdSpec("1.21.11")))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("not compatible with Minecraft version");
    }

    @Test
    void resolvePluginFile_shouldRejectMissingChecksum()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            jsonResponse(
                200,
                """
                    {
                      "id": "version01",
                      "project_id": "project01",
                      "version_number": "v5.5.0-bukkit",
                      "game_versions": ["1.21.11"],
                      "loaders": ["bukkit"],
                      "files": [
                        {
                          "url": "https://cdn.modrinth.com/data/project01/versions/version01/LuckPerms-Bukkit.jar",
                          "filename": "LuckPerms-Bukkit.jar",
                          "primary": true
                        }
                      ]
                    }
                    """
            )
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthVersionIdSpec()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("no SHA-512 checksum");
    }

    @Test
    void resolvePluginFile_shouldRejectNonSuccessApiResponse()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            jsonResponse(500, "{\"error\":\"boom\"}")
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthVersionIdSpec()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("failed with status 500");
    }

    @Test
    void resolvePluginFile_shouldRejectAmbiguousFiles()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createJsonClient(Map.of(
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

    @Test
    void resolvePluginFile_shouldRejectMultiplePrimaryFiles()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            jsonResponse(
                200,
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
                          "primary": true
                        },
                        {
                          "hashes": {"sha512": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},
                          "url": "https://cdn.modrinth.com/b.jar",
                          "filename": "b.jar",
                          "primary": true
                        }
                      ]
                    }
                    """
            )
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthVersionIdSpec()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("multiple primary jar files");
    }

    @Test
    void resolvePluginFile_shouldRejectNoJarFiles()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            jsonResponse(
                200,
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
                          "url": "https://cdn.modrinth.com/a.txt",
                          "filename": "a.txt",
                          "primary": true
                        }
                      ]
                    }
                    """
            )
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthVersionIdSpec()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("no installable jar file");
    }

    @Test
    void resolvePluginFile_shouldSelectRequiredJarWhenMultipleCandidatesExist()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            jsonResponse(
                200,
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
                          "url": "https://cdn.modrinth.com/b.jar",
                          "filename": "b.jar",
                          "primary": false
                        },
                        {
                          "hashes": {"sha512": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},
                          "url": "https://cdn.modrinth.com/a.jar",
                          "filename": "a.jar",
                          "primary": false,
                          "file_type": "unknown"
                        }
                      ]
                    }
                    """
            )
        ));

        // execute
        final ModrinthPluginMetadata metadata = client.resolvePluginFile(modrinthVersionIdSpec());

        // verify
        assertThat(metadata.fileName()).isEqualTo("b.jar");
    }

    @Test
    void resolvePluginFile_shouldRejectMultipleRequiredJarCandidates()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            jsonResponse(
                200,
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
            )
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthVersionIdSpec()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("ambiguous jar files for loader 'bukkit'")
            .hasMessageContaining("choose a version with one primary jar");
    }

    @Test
    void resolvePluginFile_shouldReportConfiguredLoaderWhenJarSelectionIsAmbiguous()
        throws Exception
    {
        // setup
        final URI versionUri = URI.create("https://api.modrinth.com/v2/version/version01");
        final ModrinthDownloadsClient client = createClient(Map.of(
            versionUri,
            jsonResponse(
                200,
                """
                    {
                      "id": "version01",
                      "project_id": "project01",
                      "version_number": "v5.5.0-paper",
                      "game_versions": ["1.21.11"],
                      "loaders": ["paper"],
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
            )
        ));

        // execute + verify
        assertThatThrownBy(() -> client.resolvePluginFile(modrinthVersionIdSpec("paper", null)))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("ambiguous jar files for loader 'paper'");
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
        return modrinthVersionIdSpec(null);
    }

    private static PluginArtifactSpec modrinthVersionIdSpec(@Nullable String gameVersion)
    {
        return modrinthVersionIdSpec("bukkit", gameVersion);
    }

    private static PluginArtifactSpec modrinthVersionIdSpec(String loader, @Nullable String gameVersion)
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
            loader,
            gameVersion
        );
    }

    private record ResponseSpec(int statusCode, String body)
    {
    }

    private static ModrinthDownloadsClient createJsonClient(Map<URI, String> responses)
        throws Exception
    {
        return createClient(responses.entrySet().stream().collect(java.util.stream.Collectors.toMap(
            Map.Entry::getKey,
            entry -> new ResponseSpec(200, entry.getValue())
        )));
    }

    private static ModrinthDownloadsClient createClient(Map<URI, ResponseSpec> responses)
        throws Exception
    {
        final HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(
            argThat((HttpRequest request) -> responses.containsKey(request.uri())),
            any(HttpResponse.BodyHandler.class)
        )).thenAnswer(invocation -> {
            final HttpRequest request = invocation.getArgument(0);
            final HttpResponse<String> response = mock(HttpResponse.class);
            final ResponseSpec responseSpec = Objects.requireNonNull(responses.get(request.uri()));
            when(response.statusCode()).thenReturn(responseSpec.statusCode());
            when(response.body()).thenReturn(responseSpec.body());
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

    private static ResponseSpec jsonResponse(int statusCode, String body)
    {
        return new ResponseSpec(statusCode, body);
    }
}
