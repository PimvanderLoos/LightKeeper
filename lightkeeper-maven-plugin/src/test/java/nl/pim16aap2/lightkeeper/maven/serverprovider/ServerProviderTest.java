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

    @Test
    void acceptEula_shouldCreateFileWithExpectedContent(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        Files.createDirectories(provider.baseServerDirectoryForTests());

        // execute
        provider.acceptEulaForTests();

        // verify
        assertThat(provider.baseServerDirectoryForTests().resolve("eula.txt"))
            .isRegularFile()
            .hasContent("eula=true");
    }

    @Test
    void writeServerProperties_shouldCreatePropertiesFile(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        Files.createDirectories(provider.baseServerDirectoryForTests());

        // execute
        provider.writeServerPropertiesForTests("motd=test");

        // verify
        assertThat(provider.baseServerDirectoryForTests().resolve("server.properties"))
            .isRegularFile()
            .hasContent("motd=test");
    }

    @Test
    void shouldBeRecreated_shouldReturnTrueWhenFileDoesNotExist(@TempDir Path tempDirectory)
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        final Path missingFile = tempDirectory.resolve("missing.txt");

        // execute
        final boolean shouldRecreate = provider.shouldBeRecreatedForTests(false, 7, missingFile);

        // verify
        assertThat(shouldRecreate).isTrue();
    }

    @Test
    void resolveVersionedDirectory_shouldIncludeVersionAndCacheKeyWhenEnabled(@TempDir Path tempDirectory)
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);

        // execute
        final Path resolved = provider.resolveVersionedDirectoryForTests("1.21.11", tempDirectory.resolve("root"), true);

        // verify
        assertThat(resolved.toString()).contains("1.21.11");
        assertThat(resolved.toString()).contains("cache-key");
    }

    @Test
    void baseServerVersionMatches_shouldReturnFalseWhenBaseServerJarIsMissing(@TempDir Path tempDirectory)
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);

        // execute
        final boolean matches = provider.baseServerVersionMatchesForTests();

        // verify
        assertThat(matches).isFalse();
    }

    @Test
    void copyJarFromCacheToBaseServer_shouldCopyJarFile(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        Files.createDirectories(provider.jarCacheDirectoryForTests());
        Files.createDirectories(provider.baseServerDirectoryForTests());
        Files.writeString(provider.jarCacheFileForTests(), "jar-content");

        // execute
        provider.copyJarFromCacheToBaseServerForTests();

        // verify
        assertThat(provider.baseServerJarFileForTests()).isRegularFile().hasContent("jar-content");
    }

    @Test
    void cleanTransientFilesForRetry_shouldDeleteKnownTransientFiles(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        final Path baseDirectory = provider.baseServerDirectoryForTests();
        Files.createDirectories(baseDirectory.resolve("nested"));
        Files.writeString(baseDirectory.resolve("session.lock"), "lock");
        Files.writeString(baseDirectory.resolve("nested/server.pid"), "pid");
        Files.writeString(baseDirectory.resolve("nested/server.sock"), "sock");
        Files.writeString(baseDirectory.resolve("nested/hs_err_pid10.log"), "error");
        Files.writeString(baseDirectory.resolve("nested/keep.txt"), "keep");

        // execute
        provider.cleanTransientFilesForRetryForTests();

        // verify
        assertThat(baseDirectory.resolve("session.lock")).doesNotExist();
        assertThat(baseDirectory.resolve("nested/server.pid")).doesNotExist();
        assertThat(baseDirectory.resolve("nested/server.sock")).doesNotExist();
        assertThat(baseDirectory.resolve("nested/hs_err_pid10.log")).doesNotExist();
        assertThat(baseDirectory.resolve("nested/keep.txt")).isRegularFile();
    }

    @Test
    void downloadFile_shouldThrowExceptionWhenCopyFails(@TempDir Path tempDirectory)
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        final String source = tempDirectory.resolve("missing.bin").toUri().toString();
        final Path target = tempDirectory.resolve("target.bin");

        // execute + verify
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.downloadFileForTests(source, target))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Failed to download file");
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

        private void acceptEulaForTests()
            throws MojoExecutionException
        {
            acceptEula();
        }

        private void writeServerPropertiesForTests(String properties)
            throws MojoExecutionException
        {
            writeServerProperties(properties);
        }

        private boolean shouldBeRecreatedForTests(boolean forceRecreate, int expiryDays, Path file)
        {
            return shouldBeRecreated(forceRecreate, expiryDays, file);
        }

        private Path resolveVersionedDirectoryForTests(String serverVersion, Path directoryRoot, boolean versioned)
        {
            return resolveVersionedDirectory(serverVersion, directoryRoot, versioned);
        }

        private boolean baseServerVersionMatchesForTests()
        {
            return baseServerVersionMatches();
        }

        private Path jarCacheDirectoryForTests()
        {
            return jarCacheDirectory();
        }

        private Path jarCacheFileForTests()
        {
            return jarCacheFile();
        }

        private Path baseServerJarFileForTests()
        {
            return baseServerJarFile();
        }

        private void copyJarFromCacheToBaseServerForTests()
            throws MojoExecutionException
        {
            copyJarFromCacheToBaseServer();
        }

        private void cleanTransientFilesForRetryForTests()
            throws MojoExecutionException
        {
            cleanTransientFilesForRetry();
        }

        private void downloadFileForTests(String url, Path targetFile)
            throws MojoExecutionException
        {
            downloadFile(url, targetFile);
        }
    }
}
