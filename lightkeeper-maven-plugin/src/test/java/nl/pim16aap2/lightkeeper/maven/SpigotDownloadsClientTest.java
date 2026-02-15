package nl.pim16aap2.lightkeeper.maven;

import com.sun.net.httpserver.HttpServer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpigotDownloadsClientTest
{
    @Test
    void resolveBuild_shouldIncludeResolvedBuildToolsBuildNumberInIdentity()
        throws Exception
    {
        // setup
        final HttpFixtureServer fixtureServer = new HttpFixtureServer("""
            {"number":197,"url":"https://hub.spigotmc.org/jenkins/job/BuildTools/197/"}
            """);
        final Log log = mock();
        final PaperDownloadsClient paperDownloadsClient = mock();
        final URI buildToolsUri = URI.create("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar");
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            log,
            paperDownloadsClient,
            "LightKeeper/Test",
            buildToolsUri,
            fixtureServer.metadataUri()
        );

        try
        {
            // execute
            final SpigotBuildMetadata spigotBuildMetadata = spigotDownloadsClient.resolveBuild("1.21.11");

            // verify
            assertThat(spigotBuildMetadata.minecraftVersion()).isEqualTo("1.21.11");
            assertThat(spigotBuildMetadata.buildToolsUri()).isEqualTo(buildToolsUri);
            assertThat(spigotBuildMetadata.buildToolsIdentity()).isEqualTo("spigot-buildtools-build-197");
        }
        finally
        {
            fixtureServer.close();
        }
    }

    @Test
    void resolveBuild_shouldUsePaperVersionForLatestSupported()
        throws Exception
    {
        // setup
        final HttpFixtureServer fixtureServer = new HttpFixtureServer("""
            {"number":201}
            """);
        final Log log = mock();
        final PaperDownloadsClient paperDownloadsClient = mock();
        when(paperDownloadsClient.resolveBuild("latest-supported"))
            .thenReturn(new PaperBuildMetadata(
                "1.21.11",
                116L,
                URI.create("https://example.invalid/paper.jar"),
                "abc"
            ));
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            log,
            paperDownloadsClient,
            "LightKeeper/Test",
            URI.create("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"),
            fixtureServer.metadataUri()
        );

        try
        {
            // execute
            final SpigotBuildMetadata spigotBuildMetadata = spigotDownloadsClient.resolveBuild("latest-supported");

            // verify
            assertThat(spigotBuildMetadata.minecraftVersion()).isEqualTo("1.21.11");
            assertThat(spigotBuildMetadata.buildToolsIdentity()).isEqualTo("spigot-buildtools-build-201");
            verify(paperDownloadsClient).resolveBuild("latest-supported");
        }
        finally
        {
            fixtureServer.close();
        }
    }

    @Test
    void resolveBuild_shouldThrowExceptionWhenBuildToolsMetadataHasNoBuildNumber()
        throws Exception
    {
        // setup
        final HttpFixtureServer fixtureServer = new HttpFixtureServer("""
            {"status":"ok"}
            """);
        final Log log = mock();
        final PaperDownloadsClient paperDownloadsClient = mock();
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            log,
            paperDownloadsClient,
            "LightKeeper/Test",
            URI.create("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"),
            fixtureServer.metadataUri()
        );

        try
        {
            // execute
            assertThatThrownBy(() -> spigotDownloadsClient.resolveBuild("1.21.11"))
                // verify
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("BuildTools build number");
        }
        finally
        {
            fixtureServer.close();
        }
    }

    private static final class HttpFixtureServer implements AutoCloseable
    {
        private final HttpServer httpServer;

        private HttpFixtureServer(String metadataResponseBody)
            throws IOException
        {
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            httpServer.createContext("/metadata", exchange ->
            {
                final byte[] responseBytes = metadataResponseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            });
            httpServer.start();
        }

        private URI metadataUri()
        {
            return URI.create("http://127.0.0.1:%d/metadata".formatted(httpServer.getAddress().getPort()));
        }

        @Override
        public void close()
        {
            httpServer.stop(0);
        }
    }
}
