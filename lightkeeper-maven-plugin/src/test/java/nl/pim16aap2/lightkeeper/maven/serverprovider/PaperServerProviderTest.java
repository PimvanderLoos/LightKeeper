package nl.pim16aap2.lightkeeper.maven.serverprovider;

import nl.pim16aap2.lightkeeper.maven.PaperBuildMetadata;
import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import nl.pim16aap2.lightkeeper.maven.serverprocess.MinecraftServerProcess;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

class PaperServerProviderTest
{
    private static final String PAPER_BINARY = "paper-binary";
    private static final String PAPER_BINARY_SHA256 = sha256Hex(PAPER_BINARY);

    private static final PaperBuildMetadata PAPER_BUILD_METADATA = new PaperBuildMetadata(
        "1.21.11",
        113,
        URI.create("https://example.com/paper.jar"),
        "unused-for-tests"
    );

    @Test
    void prepareServer_shouldRetryServerStartupWithoutRebuildingJar(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestPaperServerProvider provider = createProvider(tempDirectory, 2, 1, false);

        // execute
        provider.prepareServer();

        // verify
        assertThat(provider.createBaseServerJarInvocations()).isEqualTo(1);
        assertThat(provider.createServerProcessInvocations()).isEqualTo(2);
        assertThat(provider.targetServerDirectoryPath().resolve("paper-1.21.11.jar")).isRegularFile();
    }

    @Test
    void prepareServer_shouldDeleteTransientFilesBeforeRetry(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestPaperServerProvider provider = createProvider(tempDirectory, 2, 1, true);

        // execute
        provider.prepareServer();

        // verify
        final Path baseLockFile = provider.baseServerDirectoryForTests().resolve("world/session.lock");
        final Path targetLockFile = provider.targetServerDirectoryPath().resolve("world/session.lock");
        assertThat(baseLockFile).doesNotExist();
        assertThat(targetLockFile).doesNotExist();
    }

    @Test
    void prepareServer_shouldFailAfterMaxStartupAttemptsWithoutRebuildingJar(@TempDir Path tempDirectory)
    {
        // setup
        final TestPaperServerProvider provider = createProvider(tempDirectory, 2, 2, false);

        // execute
        final var thrown = assertThatThrownBy(provider::prepareServer);

        // verify
        thrown.isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("failed to start after 2 attempt(s)");
        assertThat(provider.createBaseServerJarInvocations()).isEqualTo(1);
        assertThat(provider.createServerProcessInvocations()).isEqualTo(2);
    }

    @Test
    void prepareServer_shouldPruneExpiredUnusedCacheDirectoriesWhenCleanupEnabled(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestPaperServerProvider provider = createProvider(tempDirectory, 2, 0, false, true);
        final Path expiredJarSibling =
            Objects.requireNonNull(provider.jarCacheDirectoryForTests().getParent()).resolve("old-jar-key");
        final Path expiredBaseSibling =
            Objects.requireNonNull(provider.baseServerDirectoryForTests().getParent()).resolve("old-base-key");
        Files.createDirectories(expiredJarSibling);
        Files.createDirectories(expiredBaseSibling);
        Files.setLastModifiedTime(expiredJarSibling, FileTime.from(Instant.now().minusSeconds(40L * 24 * 60 * 60)));
        Files.setLastModifiedTime(expiredBaseSibling, FileTime.from(Instant.now().minusSeconds(40L * 24 * 60 * 60)));

        // execute
        provider.prepareServer();

        // verify
        assertThat(expiredJarSibling).doesNotExist();
        assertThat(expiredBaseSibling).doesNotExist();
    }

    @Test
    void prepareServer_shouldNotPruneUnusedCacheDirectoriesWhenCleanupDisabled(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestPaperServerProvider provider = createProvider(tempDirectory, 2, 0, false, false);
        final Path expiredJarSibling =
            Objects.requireNonNull(provider.jarCacheDirectoryForTests().getParent()).resolve("old-jar-key");
        final Path expiredBaseSibling =
            Objects.requireNonNull(provider.baseServerDirectoryForTests().getParent()).resolve("old-base-key");
        Files.createDirectories(expiredJarSibling);
        Files.createDirectories(expiredBaseSibling);
        Files.setLastModifiedTime(expiredJarSibling, FileTime.from(Instant.now().minusSeconds(40L * 24 * 60 * 60)));
        Files.setLastModifiedTime(expiredBaseSibling, FileTime.from(Instant.now().minusSeconds(40L * 24 * 60 * 60)));

        // execute
        provider.prepareServer();

        // verify
        assertThat(expiredJarSibling).isDirectory();
        assertThat(expiredBaseSibling).isDirectory();
    }

    @Test
    void prepareServer_shouldRetainFreshUnusedCacheDirectories(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestPaperServerProvider provider = createProvider(tempDirectory, 2, 0, false, true);
        final Path freshJarSibling =
            Objects.requireNonNull(provider.jarCacheDirectoryForTests().getParent()).resolve("fresh-jar-key");
        final Path freshBaseSibling =
            Objects.requireNonNull(provider.baseServerDirectoryForTests().getParent()).resolve("fresh-base-key");
        Files.createDirectories(freshJarSibling);
        Files.createDirectories(freshBaseSibling);
        Files.setLastModifiedTime(freshJarSibling, FileTime.from(Instant.now().minusSeconds(2L * 24 * 60 * 60)));
        Files.setLastModifiedTime(freshBaseSibling, FileTime.from(Instant.now().minusSeconds(2L * 24 * 60 * 60)));

        // execute
        provider.prepareServer();

        // verify
        assertThat(freshJarSibling).isDirectory();
        assertThat(freshBaseSibling).isDirectory();
    }

    @Test
    void createBaseServerJar_shouldWriteJarWhenChecksumMatches(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PaperBuildMetadata metadata = new PaperBuildMetadata(
            "1.21.11",
            113,
            URI.create("https://example.com/paper.jar"),
            PAPER_BINARY_SHA256
        );
        final TestPaperJarProvider provider = createJarProvider(tempDirectory, metadata, PAPER_BINARY);

        // execute
        provider.runCreateBaseServerJar();

        // verify
        assertThat(provider.jarCacheFileForTests()).isRegularFile().hasContent(PAPER_BINARY);
    }

    @Test
    void createBaseServerJar_shouldThrowExceptionWhenChecksumDoesNotMatch(@TempDir Path tempDirectory)
    {
        // setup
        final PaperBuildMetadata metadata = new PaperBuildMetadata(
            "1.21.11",
            113,
            URI.create("https://example.com/paper.jar"),
            "deadbeef"
        );
        final TestPaperJarProvider provider = createJarProvider(tempDirectory, metadata, PAPER_BINARY);

        // execute + verify
        assertThatThrownBy(provider::runCreateBaseServerJar)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Checksum mismatch");
    }

    private TestPaperServerProvider createProvider(
        Path tempDirectory,
        int maxAttempts,
        int failingAttempts,
        boolean createTransientLockOnFailure)
    {
        return createProvider(tempDirectory, maxAttempts, failingAttempts, createTransientLockOnFailure, true);
    }

    private TestPaperServerProvider createProvider(
        Path tempDirectory,
        int maxAttempts,
        int failingAttempts,
        boolean createTransientLockOnFailure,
        boolean cleanupUnusedCacheDirectories)
    {
        final Log log = new SystemStreamLog();
        final ServerSpecification serverSpecification = new ServerSpecification(
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
            cleanupUnusedCacheDirectories,
            5,
            5,
            maxAttempts,
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

        return new TestPaperServerProvider(
            log,
            serverSpecification,
            PAPER_BUILD_METADATA,
            failingAttempts,
            createTransientLockOnFailure
        );
    }

    private TestPaperJarProvider createJarProvider(
        Path tempDirectory,
        PaperBuildMetadata metadata,
        String downloadedJarContents)
    {
        final ServerSpecification serverSpecification = new ServerSpecification(
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
            1,
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
        return new TestPaperJarProvider(
            new SystemStreamLog(),
            serverSpecification,
            metadata,
            downloadedJarContents
        );
    }

    private static final class TestPaperServerProvider extends PaperServerProvider
    {
        private final int failingAttempts;
        private final boolean createTransientLockOnFailure;
        private int createBaseServerJarInvocations;
        private int createServerProcessInvocations;

        private TestPaperServerProvider(
            Log log,
            ServerSpecification serverSpecification,
            PaperBuildMetadata paperBuildMetadata,
            int failingAttempts,
            boolean createTransientLockOnFailure)
        {
            super(log, serverSpecification, paperBuildMetadata);
            this.failingAttempts = failingAttempts;
            this.createTransientLockOnFailure = createTransientLockOnFailure;
        }

        @Override
        protected void createBaseServerJar()
            throws MojoExecutionException
        {
            createBaseServerJarInvocations++;
            try
            {
                Files.createDirectories(jarCacheFile().getParent());
                Files.writeString(jarCacheFile(), "paper-jar");
            }
            catch (IOException exception)
            {
                throw new MojoExecutionException("Failed to create test server jar.", exception);
            }
        }

        @Override
        protected MinecraftServerProcess createServerProcess()
        {
            createServerProcessInvocations++;
            final boolean failStart = createServerProcessInvocations <= failingAttempts;
            return new ScriptedServerProcess(baseServerDirectory(), failStart, createTransientLockOnFailure);
        }

        int createBaseServerJarInvocations()
        {
            return createBaseServerJarInvocations;
        }

        int createServerProcessInvocations()
        {
            return createServerProcessInvocations;
        }

        Path baseServerDirectoryForTests()
        {
            return baseServerDirectory();
        }

        Path jarCacheDirectoryForTests()
        {
            return jarCacheDirectory();
        }
    }

    private static final class ScriptedServerProcess extends MinecraftServerProcess
    {
        private final Path serverDirectory;
        private final boolean failStart;
        private final boolean createTransientLockOnFailure;
        private boolean running;

        private ScriptedServerProcess(Path serverDirectory, boolean failStart, boolean createTransientLockOnFailure)
        {
            super(serverDirectory, serverDirectory.resolve("paper.jar"), "java", null, 512);
            this.serverDirectory = serverDirectory;
            this.failStart = failStart;
            this.createTransientLockOnFailure = createTransientLockOnFailure;
        }

        @Override
        public void start(int timeoutSeconds)
            throws MojoExecutionException
        {
            if (failStart)
            {
                if (createTransientLockOnFailure)
                    createTransientFile();
                throw new MojoExecutionException("Simulated startup failure");
            }

            running = true;
        }

        @Override
        public void stop(int timeoutSeconds)
            throws MojoExecutionException
        {
            if (!running)
                throw new MojoExecutionException("Process not running.");
            running = false;
        }

        @Override
        public boolean isRunning()
        {
            return running;
        }

        private void createTransientFile()
            throws MojoExecutionException
        {
            final Path lockFile = serverDirectory.resolve("world/session.lock");
            try
            {
                Files.createDirectories(lockFile.getParent());
                Files.writeString(lockFile, "locked");
            }
            catch (IOException exception)
            {
                throw new MojoExecutionException("Failed to create transient lock file.", exception);
            }
        }
    }

    private static final class TestPaperJarProvider extends PaperServerProvider
    {
        private final String downloadedJarContents;

        private TestPaperJarProvider(
            Log log,
            ServerSpecification serverSpecification,
            PaperBuildMetadata paperBuildMetadata,
            String downloadedJarContents)
        {
            super(log, serverSpecification, paperBuildMetadata);
            this.downloadedJarContents = downloadedJarContents;
        }

        private void runCreateBaseServerJar()
            throws MojoExecutionException
        {
            createBaseServerJar();
        }

        private Path jarCacheFileForTests()
        {
            return jarCacheFile();
        }

        @Override
        protected void downloadFile(String url, Path targetFile)
            throws MojoExecutionException
        {
            try
            {
                Files.createDirectories(targetFile.getParent());
                Files.writeString(targetFile, downloadedJarContents);
            }
            catch (IOException exception)
            {
                throw new MojoExecutionException("Failed to write fake jar.", exception);
            }
        }

        @Override
        protected void createBaseServer()
        {
            // Not used in these focused tests.
        }
    }

    private static String sha256Hex(String input)
    {
        try
        {
            return HexFormat.of().formatHex(
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }
}
