package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import com.sun.net.httpserver.HttpServer;
import nl.pim16aap2.lightkeeper.maven.ModrinthDownloadsClient;
import nl.pim16aap2.lightkeeper.maven.ModrinthPluginMetadata;
import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrepareServerPluginArtifactResolverTest
{
    @Test
    void resolvePluginArtifacts_shouldResolvePathSourceWithRename(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path sourceJar = Files.writeString(tempDirectory.resolve("source.jar"), "plugin");
        final PluginArtifactSpec spec = new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.PATH,
            sourceJar,
            null,
            null,
            null,
            null,
            "jar",
            false,
            "renamed.jar"
        );
        final PrepareServerPluginArtifactResolver resolver = new PrepareServerPluginArtifactResolver();

        // execute
        final List<ResolvedPluginArtifact> resolved = resolver.resolvePluginArtifacts(
            List.of(spec),
            mock(RepositorySystem.class),
            mock(RepositorySystemSession.class),
            List.of(),
            tempDirectory.resolve("cache"),
            "LightKeeper/Test",
            new SystemStreamLog()
        );

        // verify
        assertThat(resolved).singleElement().satisfies(artifact -> {
            assertThat(artifact.sourceJar()).isEqualTo(sourceJar);
            assertThat(artifact.outputFileName()).isEqualTo("renamed.jar");
            assertThat(artifact.sourceDescription()).isEqualTo("path:" + sourceJar);
        });
    }

    @Test
    void resolvePluginArtifacts_shouldResolveSingleMavenArtifact(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path sourceJar = Files.writeString(tempDirectory.resolve("fixture.jar"), "plugin");
        final RepositorySystem repositorySystem = mock(RepositorySystem.class);
        final RepositorySystemSession repositorySystemSession = mock(RepositorySystemSession.class);
        final ArtifactRequest request = new ArtifactRequest()
            .setArtifact(new DefaultArtifact("com.example:fixture:jar:1.0.0"))
            .setRepositories(List.of());
        when(repositorySystem.resolveArtifact(any(), any())).thenReturn(new ArtifactResult(request)
            .setArtifact(new DefaultArtifact("com.example:fixture:jar:1.0.0").setFile(sourceJar.toFile())));
        final PrepareServerPluginArtifactResolver resolver = new PrepareServerPluginArtifactResolver();

        // execute
        final List<ResolvedPluginArtifact> resolved = resolver.resolvePluginArtifacts(
            List.of(mavenSpec(false, "renamed.jar")),
            repositorySystem,
            repositorySystemSession,
            List.of(),
            tempDirectory.resolve("cache"),
            "LightKeeper/Test",
            new SystemStreamLog()
        );

        // verify
        assertThat(resolved).singleElement().satisfies(artifact -> {
            assertThat(artifact.sourceJar()).isEqualTo(sourceJar);
            assertThat(artifact.outputFileName()).isEqualTo("renamed.jar");
            assertThat(artifact.sourceDescription()).startsWith("maven:com.example:fixture:jar:1.0.0");
        });
    }

    @Test
    void resolvePluginArtifacts_shouldResolveTransitiveMavenArtifacts(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path rootJar = Files.writeString(tempDirectory.resolve("fixture.jar"), "root");
        final Path dependencyJar = Files.writeString(tempDirectory.resolve("dependency.jar"), "dependency");
        final RepositorySystem repositorySystem = mock(RepositorySystem.class);
        final RepositorySystemSession repositorySystemSession = mock(RepositorySystemSession.class);
        final DependencyRequest request = new DependencyRequest();
        final DependencyResult dependencyResult = new DependencyResult(request).setArtifactResults(List.of(
            new ArtifactResult(new ArtifactRequest())
                .setArtifact(new DefaultArtifact("com.example:fixture:jar:1.0.0").setFile(rootJar.toFile())),
            new ArtifactResult(new ArtifactRequest())
                .setArtifact(new DefaultArtifact("com.example:dependency:jar:1.0.0").setFile(dependencyJar.toFile()))
        ));
        when(repositorySystem.resolveDependencies(any(), any())).thenReturn(dependencyResult);
        final PrepareServerPluginArtifactResolver resolver = new PrepareServerPluginArtifactResolver();

        // execute
        final List<ResolvedPluginArtifact> resolved = resolver.resolvePluginArtifacts(
            List.of(mavenSpec(true, null)),
            repositorySystem,
            repositorySystemSession,
            List.of(),
            tempDirectory.resolve("cache"),
            "LightKeeper/Test",
            new SystemStreamLog()
        );

        // verify
        assertThat(resolved).extracting(ResolvedPluginArtifact::outputFileName)
            .containsExactly("fixture.jar", "dependency.jar");
        assertThat(resolved).extracting(ResolvedPluginArtifact::sourceJar)
            .containsExactly(rootJar, dependencyJar);
    }

    @Test
    void resolvePluginArtifacts_shouldDownloadUrlSourceToCacheAndReuseCache(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final byte[] pluginBytes = "plugin".getBytes(StandardCharsets.UTF_8);
        final AtomicInteger requestCount = new AtomicInteger();
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/plugin.jar", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(200, pluginBytes.length);
            exchange.getResponseBody().write(pluginBytes);
            exchange.close();
        });
        server.start();
        final URI uri = URI.create("http://127.0.0.1:%d/plugin.jar".formatted(server.getAddress().getPort()));
        final PluginArtifactSpec spec = urlSpec(uri, HashUtil.sha256(pluginBytes));
        final PrepareServerPluginArtifactResolver resolver = new PrepareServerPluginArtifactResolver();

        try
        {
            // execute
            final List<ResolvedPluginArtifact> firstResolved = resolver.resolvePluginArtifacts(
                List.of(spec),
                mock(RepositorySystem.class),
                mock(RepositorySystemSession.class),
                List.of(),
                tempDirectory.resolve("cache"),
                "LightKeeper/Test",
                new SystemStreamLog()
            );
            server.stop(0);
            final List<ResolvedPluginArtifact> secondResolved = resolver.resolvePluginArtifacts(
                List.of(spec),
                mock(RepositorySystem.class),
                mock(RepositorySystemSession.class),
                List.of(),
                tempDirectory.resolve("cache"),
                "LightKeeper/Test",
                new SystemStreamLog()
            );

            // verify
            assertThat(firstResolved).singleElement().satisfies(artifact -> {
                assertThat(artifact.outputFileName()).isEqualTo("plugin.jar");
                assertThat(artifact.sourceJar()).isRegularFile();
                assertThat(artifact.sourceJar()).hasContent("plugin");
            });
            assertThat(secondResolved.getFirst().sourceJar()).isEqualTo(firstResolved.getFirst().sourceJar());
            assertThat(requestCount).hasValue(1);
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void resolvePluginArtifacts_shouldRejectCorruptCachedUrlArtifact(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final byte[] pluginBytes = "plugin".getBytes(StandardCharsets.UTF_8);
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/plugin.jar", exchange -> {
            exchange.sendResponseHeaders(200, pluginBytes.length);
            exchange.getResponseBody().write(pluginBytes);
            exchange.close();
        });
        server.start();
        final URI uri = URI.create("http://127.0.0.1:%d/plugin.jar".formatted(server.getAddress().getPort()));
        final PluginArtifactSpec spec = urlSpec(uri, HashUtil.sha256(pluginBytes));
        final PrepareServerPluginArtifactResolver resolver = new PrepareServerPluginArtifactResolver();

        try
        {
            final List<ResolvedPluginArtifact> resolved = resolver.resolvePluginArtifacts(
                List.of(spec),
                mock(RepositorySystem.class),
                mock(RepositorySystemSession.class),
                List.of(),
                tempDirectory.resolve("cache"),
                "LightKeeper/Test",
                new SystemStreamLog()
            );
            Files.writeString(resolved.getFirst().sourceJar(), "tampered");

            // execute + verify
            assertThatThrownBy(() -> resolver.resolvePluginArtifacts(
                List.of(spec),
                mock(RepositorySystem.class),
                mock(RepositorySystemSession.class),
                List.of(),
                tempDirectory.resolve("cache"),
                "LightKeeper/Test",
                new SystemStreamLog()
            ))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("cached plugin artifact")
                .hasMessageContaining("failed SHA-256 verification");
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void resolvePluginArtifacts_shouldRejectFailedUrlDownload(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        final HttpResponse<Path> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        final PrepareServerPluginArtifactResolver resolver = new PrepareServerPluginArtifactResolver();

        // execute + verify
        assertThatThrownBy(() -> resolver.resolvePluginArtifacts(
            List.of(urlSpec(
                URI.create("https://example.com/plugin.jar"),
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            )),
            mock(RepositorySystem.class),
            mock(RepositorySystemSession.class),
            List.of(),
            tempDirectory.resolve("cache"),
            "LightKeeper/Test",
            httpClient,
            mock(ModrinthDownloadsClient.class),
            new SystemStreamLog()
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("failed with status 404");
    }

    @Test
    void resolvePluginArtifacts_shouldRejectUrlHashMismatch(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final byte[] pluginBytes = "plugin".getBytes(StandardCharsets.UTF_8);
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/plugin.jar", exchange -> {
            exchange.sendResponseHeaders(200, pluginBytes.length);
            exchange.getResponseBody().write(pluginBytes);
            exchange.close();
        });
        server.start();
        final URI uri = URI.create("http://127.0.0.1:%d/plugin.jar".formatted(server.getAddress().getPort()));
        final PrepareServerPluginArtifactResolver resolver = new PrepareServerPluginArtifactResolver();

        try
        {
            // execute + verify
            assertThatThrownBy(() -> resolver.resolvePluginArtifacts(
                List.of(urlSpec(uri, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")),
                mock(RepositorySystem.class),
                mock(RepositorySystemSession.class),
                List.of(),
                tempDirectory.resolve("cache"),
                "LightKeeper/Test",
                new SystemStreamLog()
            ))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("failed SHA-256 verification");
            try (Stream<Path> cachedFiles = Files.walk(tempDirectory.resolve("cache")))
            {
                assertThat(cachedFiles
                    .filter(path -> path.getFileName().toString().equals("plugin.jar"))
                    .toList()).isEmpty();
            }
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void resolvePluginArtifacts_shouldDownloadModrinthSourceToCacheAndReuseCache(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final byte[] pluginBytes = "modrinth-plugin".getBytes(StandardCharsets.UTF_8);
        final AtomicInteger requestCount = new AtomicInteger();
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/plugin.jar", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(200, pluginBytes.length);
            exchange.getResponseBody().write(pluginBytes);
            exchange.close();
        });
        server.start();
        final URI downloadUri = URI.create("http://127.0.0.1:%d/plugin.jar".formatted(server.getAddress().getPort()));
        final Path hashSource = Files.write(tempDirectory.resolve("modrinth.jar"), pluginBytes);
        final ModrinthPluginMetadata metadata = new ModrinthPluginMetadata(
            "version01",
            "v5.5.0-bukkit",
            "LuckPerms-Bukkit.jar",
            downloadUri,
            HashUtil.sha512(hashSource)
        );
        final ModrinthDownloadsClient modrinthDownloadsClient = mock(ModrinthDownloadsClient.class);
        final PluginArtifactSpec spec = modrinthSpec();
        when(modrinthDownloadsClient.resolvePluginFile(spec)).thenReturn(metadata);
        final PrepareServerPluginArtifactResolver resolver = new PrepareServerPluginArtifactResolver();
        final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        try
        {
            // execute
            final List<ResolvedPluginArtifact> firstResolved = resolver.resolvePluginArtifacts(
                List.of(spec),
                mock(RepositorySystem.class),
                mock(RepositorySystemSession.class),
                List.of(),
                tempDirectory.resolve("cache"),
                "LightKeeper/Test",
                httpClient,
                modrinthDownloadsClient,
                new SystemStreamLog()
            );
            final List<ResolvedPluginArtifact> secondResolved = resolver.resolvePluginArtifacts(
                List.of(spec),
                mock(RepositorySystem.class),
                mock(RepositorySystemSession.class),
                List.of(),
                tempDirectory.resolve("cache"),
                "LightKeeper/Test",
                httpClient,
                modrinthDownloadsClient,
                new SystemStreamLog()
            );

            // verify
            assertThat(firstResolved).singleElement().satisfies(artifact -> {
                assertThat(artifact.outputFileName()).isEqualTo("LuckPerms-Bukkit.jar");
                assertThat(artifact.sourceJar()).isRegularFile();
                assertThat(artifact.sourceJar()).hasContent("modrinth-plugin");
            });
            assertThat(secondResolved.getFirst().sourceJar()).isEqualTo(firstResolved.getFirst().sourceJar());
            assertThat(requestCount).hasValue(1);
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void resolvePluginArtifacts_shouldRejectModrinthFilenameWithoutJarSuffix(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final URI downloadUri = URI.create("https://example.com/modrinth/plugin.txt");
        final ModrinthPluginMetadata metadata = new ModrinthPluginMetadata(
            "version01",
            "v5.5.0-bukkit",
            "plugin.txt",
            downloadUri,
            HashUtil.sha512(new ByteArrayInputStream("modrinth-plugin".getBytes(StandardCharsets.UTF_8)))
        );
        final ModrinthDownloadsClient modrinthDownloadsClient = mock(ModrinthDownloadsClient.class);
        final PluginArtifactSpec spec = modrinthSpec();
        when(modrinthDownloadsClient.resolvePluginFile(spec)).thenReturn(metadata);
        final PrepareServerPluginArtifactResolver resolver = new PrepareServerPluginArtifactResolver();

        // execute + verify
        assertThatThrownBy(() -> resolver.resolvePluginArtifacts(
            List.of(spec),
            mock(RepositorySystem.class),
            mock(RepositorySystemSession.class),
            List.of(),
            tempDirectory.resolve("cache"),
            "LightKeeper/Test",
            mock(HttpClient.class),
            modrinthDownloadsClient,
            new SystemStreamLog()
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("must end with .jar");
    }

    private static PluginArtifactSpec urlSpec(URI uri, String sha256)
    {
        return new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.URL,
            null,
            null,
            null,
            null,
            null,
            "jar",
            false,
            "plugin.jar",
            uri,
            sha256,
            null,
            null,
            null,
            null,
            null
        );
    }

    private static PluginArtifactSpec modrinthSpec()
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
            "version01",
            "bukkit",
            null
        );
    }

    private static PluginArtifactSpec mavenSpec(boolean includeTransitive, @Nullable String renameTo)
    {
        return new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.MAVEN,
            null,
            "com.example",
            "fixture",
            "1.0.0",
            null,
            "jar",
            includeTransitive,
            renameTo,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
