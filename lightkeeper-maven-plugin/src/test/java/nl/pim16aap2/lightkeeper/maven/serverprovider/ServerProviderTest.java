package nl.pim16aap2.lightkeeper.maven.serverprovider;

import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ServerProviderTest
{
    @Test
    void createDefaultServerProperties_shouldReuseReservedPort(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);

        // execute
        final String content = provider.createDefaultServerPropertiesForTests();
        final Properties properties = new Properties();
        properties.load(new StringReader(content));

        // verify
        assertThat(properties.getProperty("online-mode")).isEqualTo("false");
        assertThat(properties.getProperty("enable-query")).isEqualTo("true");
        assertThat(properties.getProperty("enable-rcon")).isEqualTo("false");
        assertThat(properties.getProperty("query.port")).matches("\\d+");
        assertThat(properties.getProperty("server-port")).isEqualTo(properties.getProperty("query.port"));
    }

    @Test
    void configureSpigotWatchdogTimeout_shouldUpdateExistingTimeoutValue(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        final Path spigotConfiguration = provider.baseServerDirectoryForTests().resolve("spigot.yml");
        Files.createDirectories(provider.baseServerDirectoryForTests());
        Files.write(
            spigotConfiguration,
            List.of(
                "settings:",
                "  bungeecord: false",
                "  timeout-time: 60"
            )
        );

        // execute
        provider.configureSpigotWatchdogTimeoutForTests();

        // verify
        assertThat(Files.readAllLines(spigotConfiguration))
            .contains("  timeout-time: 600")
            .doesNotContain("  timeout-time: 60");
    }

    @Test
    void configureSpigotWatchdogTimeout_shouldInsertTimeoutUnderSettingsSection(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        final Path spigotConfiguration = provider.baseServerDirectoryForTests().resolve("spigot.yml");
        Files.createDirectories(provider.baseServerDirectoryForTests());
        Files.write(
            spigotConfiguration,
            List.of(
                "settings:",
                "  bungeecord: false",
                "world-settings:",
                "  default:",
                "    verbose: false"
            )
        );

        // execute
        provider.configureSpigotWatchdogTimeoutForTests();
        final List<String> updatedLines = Files.readAllLines(spigotConfiguration);

        // verify
        assertThat(updatedLines).contains("  timeout-time: 600");
        assertThat(updatedLines.indexOf("  timeout-time: 600")).isEqualTo(updatedLines.indexOf("settings:") + 1);
    }

    private static TestServerProvider createProvider(Path tempDirectory)
    {
        final Log log = new SystemStreamLog();
        final ServerSpecification specification = new ServerSpecification(
            "1.21.11",
            tempDirectory.resolve("jars"),
            tempDirectory.resolve("base"),
            tempDirectory.resolve("work"),
            tempDirectory.resolve("runtime-manifest.json"),
            tempDirectory.resolve("sockets"),
            false,
            30,
            true,
            30,
            true,
            true,
            5,
            5,
            2,
            512,
            "java",
            null,
            "cache-key",
            "LightKeeper/Tests",
            null,
            null,
            "test-token",
            "v1.1",
            "no-agent"
        );
        return new TestServerProvider(log, specification);
    }

    private static final class TestServerProvider extends ServerProvider
    {
        private TestServerProvider(Log log, ServerSpecification serverSpecification)
        {
            super(log, "test", serverSpecification);
        }

        @Override
        protected void createBaseServerJar()
        {
            throw new UnsupportedOperationException("Not used in this test.");
        }

        @Override
        protected void createBaseServer()
            throws MojoExecutionException
        {
            throw new UnsupportedOperationException("Not used in this test.");
        }

        private String createDefaultServerPropertiesForTests()
            throws MojoExecutionException
        {
            return createDefaultServerProperties();
        }

        private void configureSpigotWatchdogTimeoutForTests()
            throws MojoExecutionException
        {
            configureSpigotWatchdogTimeout();
        }

        private Path baseServerDirectoryForTests()
        {
            return baseServerDirectory();
        }
    }
}
