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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaperServerProviderTest
{
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

    private TestPaperServerProvider createProvider(
        Path tempDirectory,
        int maxAttempts,
        int failingAttempts,
        boolean createTransientLockOnFailure)
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
}
