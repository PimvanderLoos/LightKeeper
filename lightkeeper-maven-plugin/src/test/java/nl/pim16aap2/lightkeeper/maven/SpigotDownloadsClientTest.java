package nl.pim16aap2.lightkeeper.maven;

import com.sun.net.httpserver.HttpServer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
        final URI buildToolsUri = URI.create("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar");
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            log,
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
    void resolveBuild_shouldUseSupportedVersionForLatestSupported()
        throws Exception
    {
        // setup
        final HttpFixtureServer fixtureServer = new HttpFixtureServer("""
            {"number":201}
            """);
        final Log log = mock();
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            log,
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
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            log,
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

    @Test
    void resolveBuild_shouldUseBuildNumberFromBuildUrlWhenNumberFieldIsMissing()
        throws Exception
    {
        // setup
        final HttpFixtureServer fixtureServer = new HttpFixtureServer(
            200,
            """
            {"url":"https://hub.spigotmc.org/jenkins/job/BuildTools/345/artifact/target/BuildTools.jar"}
            """
        );
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            mock(),
            "LightKeeper/Test",
            URI.create("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"),
            fixtureServer.metadataUri()
        );

        try
        {
            // execute
            final SpigotBuildMetadata spigotBuildMetadata = spigotDownloadsClient.resolveBuild("1.21.11");

            // verify
            assertThat(spigotBuildMetadata.buildToolsIdentity()).isEqualTo("spigot-buildtools-build-345");
        }
        finally
        {
            fixtureServer.close();
        }
    }

    @Test
    void resolveBuild_shouldThrowExceptionWhenMetadataRequestReturnsNonSuccessStatus()
        throws Exception
    {
        // setup
        final HttpFixtureServer fixtureServer = new HttpFixtureServer(503, "{\"status\":\"down\"}");
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            mock(),
            "LightKeeper/Test",
            URI.create("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"),
            fixtureServer.metadataUri()
        );

        try
        {
            // execute + verify
            assertThatThrownBy(() -> spigotDownloadsClient.resolveBuild("1.21.11"))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("failed with status 503");
        }
        finally
        {
            fixtureServer.close();
        }
    }

    @Test
    void resolveBuild_shouldThrowExceptionWhenMetadataPayloadIsInvalidJson()
        throws Exception
    {
        // setup
        final HttpFixtureServer fixtureServer = new HttpFixtureServer(200, "{ not-json ");
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            mock(),
            "LightKeeper/Test",
            URI.create("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"),
            fixtureServer.metadataUri()
        );

        try
        {
            // execute + verify
            assertThatThrownBy(() -> spigotDownloadsClient.resolveBuild("1.21.11"))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Failed to parse BuildTools metadata response");
        }
        finally
        {
            fixtureServer.close();
        }
    }

    @Test
    void constructor_shouldThrowExceptionWhenUserAgentIsBlank()
    {
        // execute + verify
        assertThatThrownBy(() -> new SpigotDownloadsClient(
            mock(),
            "   ",
            URI.create("https://example.invalid/buildtools.jar"),
            URI.create("https://example.invalid/buildtools.json")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-empty BuildTools User-Agent");
    }

    @Test
    void resolveBuild_shouldThrowExceptionWhenRequestedVersionIsBlank()
    {
        // setup
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            mock(),
            "LightKeeper/Test",
            URI.create("https://example.invalid/buildtools.jar"),
            URI.create("https://example.invalid/buildtools.json")
        );

        // execute + verify
        assertThatThrownBy(() -> spigotDownloadsClient.resolveBuild("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requestedVersion may not be blank");
    }

    @Test
    void resolveBuild_shouldThrowExceptionWhenVersionIsUnsupported()
    {
        // setup
        final SpigotDownloadsClient spigotDownloadsClient = new SpigotDownloadsClient(
            mock(),
            "LightKeeper/Test",
            URI.create("https://example.invalid/buildtools.jar"),
            URI.create("https://example.invalid/buildtools.json")
        );

        // execute + verify
        assertThatThrownBy(() -> spigotDownloadsClient.resolveBuild("26.1.2"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Unsupported Spigot version")
            .hasMessageContaining("1.21.11");
    }

    private static final class HttpFixtureServer implements AutoCloseable
    {
        private final HttpServer httpServer;

        private HttpFixtureServer(String metadataResponseBody)
            throws IOException
        {
            this(200, metadataResponseBody);
        }

        private HttpFixtureServer(int statusCode, String metadataResponseBody)
            throws IOException
        {
            httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            httpServer.createContext("/metadata", exchange ->
            {
                final byte[] responseBytes = metadataResponseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
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
