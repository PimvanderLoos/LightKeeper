package nl.pim16aap2.lightkeeper.maven.serverprovider;

import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerProviderTest
{
    @Test
    void createDefaultServerProperties_shouldUsePortAgnosticDefaults(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);

        // execute
        final String content = provider.createDefaultServerPropertiesForTests();

        // verify
        assertThat(content).contains("online-mode=false");
        assertThat(content).contains("enable-query=true");
        assertThat(content).contains("enable-rcon=false");
        assertThat(content).contains("query.port=25565");
        assertThat(content).contains("server-port=25565");
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

    @Test
    void acceptEula_shouldThrowExceptionWhenFileAlreadyExists(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        Files.createDirectories(provider.baseServerDirectoryForTests());
        Files.writeString(provider.baseServerDirectoryForTests().resolve("eula.txt"), "eula=true");

        // execute + verify
        assertThatThrownBy(provider::acceptEulaForTests)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Failed to create EULA");
    }

    @Test
    void writeServerProperties_shouldThrowExceptionWhenFileAlreadyExists(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        Files.createDirectories(provider.baseServerDirectoryForTests());
        Files.writeString(provider.baseServerDirectoryForTests().resolve("server.properties"), "a=b");

        // execute + verify
        assertThatThrownBy(() -> provider.writeServerPropertiesForTests("motd=test"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Failed to write server properties");
    }

    @Test
    void shouldBeRecreated_shouldReturnFalseWhenFileIsFresh(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        final Path file = Files.writeString(tempDirectory.resolve("fresh.txt"), "fresh");

        // execute
        final boolean shouldRecreate = provider.shouldBeRecreatedForTests(false, 7, file);

        // verify
        assertThat(shouldRecreate).isFalse();
    }

    @Test
    void shouldBeRecreated_shouldReturnTrueWhenForceRecreateIsEnabled(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        final Path file = Files.writeString(tempDirectory.resolve("existing.txt"), "content");

        // execute
        final boolean shouldRecreate = provider.shouldBeRecreatedForTests(true, 7, file);

        // verify
        assertThat(shouldRecreate).isTrue();
    }

    @Test
    void shouldBeRecreated_shouldReturnTrueWhenFileIsExpired(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory);
        final Path file = Files.writeString(tempDirectory.resolve("old.txt"), "content");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(20L * 24L * 60L * 60L)));

        // execute
        final boolean shouldRecreate = provider.shouldBeRecreatedForTests(false, 7, file);

        // verify
        assertThat(shouldRecreate).isTrue();
    }

    @Test
    void rewriteTargetServerPropertiesWithReservedPort_shouldReplaceExistingPorts(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestServerProvider provider = createProvider(tempDirectory, 26601);
        Files.createDirectories(provider.targetServerDirectoryForTests());
        final Path propertiesFile = provider.targetServerDirectoryForTests().resolve("server.properties");
        Files.writeString(
            propertiesFile,
            """
                online-mode=false
                query.port=25565
                server-port=25565
                """
        );

        // execute
        provider.rewriteTargetServerPropertiesWithReservedPortForTests();

        // verify
        final String content = Files.readString(propertiesFile);
        assertThat(content).contains("query.port=26601");
        assertThat(content).contains("server-port=26601");
    }

    private static TestServerProvider createProvider(Path tempDirectory)
    {
        return createProvider(tempDirectory, 25565);
    }

    private static TestServerProvider createProvider(Path tempDirectory, int reservedPort)
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
            "test-token",
            1,
            "no-agent"
        );
        return new TestServerProvider(log, specification, reservedPort);
    }

    private static final class TestServerProvider extends ServerProvider
    {
        private final int reservedPort;

        private TestServerProvider(Log log, ServerSpecification serverSpecification, int reservedPort)
        {
            super(log, "test", serverSpecification);
            this.reservedPort = reservedPort;
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

        @Override
        protected int reserveTargetServerPort()
        {
            return reservedPort;
        }

        private Path targetServerDirectoryForTests()
        {
            return targetServerDirectoryPath();
        }

        private void rewriteTargetServerPropertiesWithReservedPortForTests()
            throws MojoExecutionException
        {
            rewriteTargetServerPropertiesWithReservedPort();
        }
    }
}
