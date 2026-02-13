package nl.pim16aap2.lightkeeper.maven.serverprovider;

import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import nl.pim16aap2.lightkeeper.maven.SpigotBuildMetadata;
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

class SpigotServerProviderTest
{
    private static final SpigotBuildMetadata SPIGOT_BUILD_METADATA = new SpigotBuildMetadata(
        "1.21.11",
        URI.create("https://example.com/BuildTools.jar"),
        "buildtools-identity"
    );

    @Test
    void prepareServer_shouldRetryServerStartupWithoutRebuildingJar(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestSpigotServerProvider provider = createProvider(tempDirectory, 2, 1, false);

        // execute
        provider.prepareServer();

        // verify
        assertThat(provider.createBaseServerJarInvocations()).isEqualTo(1);
        assertThat(provider.createServerProcessInvocations()).isEqualTo(2);
        assertThat(provider.targetServerDirectoryPath().resolve("spigot-1.21.11.jar")).isRegularFile();
    }

    @Test
    void prepareServer_shouldDeleteTransientFilesBeforeRetry(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestSpigotServerProvider provider = createProvider(tempDirectory, 2, 1, true);

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
        final TestSpigotServerProvider provider = createProvider(tempDirectory, 2, 2, false);

        // execute
        final var thrown = assertThatThrownBy(provider::prepareServer);

        // verify
        thrown.isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("failed to start after 2 attempt(s)");
        assertThat(provider.createBaseServerJarInvocations()).isEqualTo(1);
        assertThat(provider.createServerProcessInvocations()).isEqualTo(2);
    }

    private TestSpigotServerProvider createProvider(
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

        return new TestSpigotServerProvider(
            log,
            serverSpecification,
            SPIGOT_BUILD_METADATA,
            failingAttempts,
            createTransientLockOnFailure
        );
    }

    private static final class TestSpigotServerProvider extends SpigotServerProvider
    {
        private final int failingAttempts;
        private final boolean createTransientLockOnFailure;
        private int createBaseServerJarInvocations;
        private int createServerProcessInvocations;

        private TestSpigotServerProvider(
            Log log,
            ServerSpecification serverSpecification,
            SpigotBuildMetadata spigotBuildMetadata,
            int failingAttempts,
            boolean createTransientLockOnFailure)
        {
            super(log, serverSpecification, spigotBuildMetadata);
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
                Files.writeString(jarCacheFile(), "spigot-jar");
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
            super(serverDirectory, serverDirectory.resolve("spigot.jar"), "java", null, 512);
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
