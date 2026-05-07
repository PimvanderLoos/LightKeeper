package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import com.sun.net.httpserver.HttpServer;
import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PrepareServerPluginArtifactResolverTest
{
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
}
