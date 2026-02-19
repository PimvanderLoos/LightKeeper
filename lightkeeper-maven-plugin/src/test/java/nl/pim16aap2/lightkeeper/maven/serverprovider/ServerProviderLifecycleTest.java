package nl.pim16aap2.lightkeeper.maven.serverprovider;

import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import nl.pim16aap2.lightkeeper.maven.LightkeeperEmbeddedAgent;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerProviderLifecycleTest
{
    @Test
    void prepareServer_shouldCreateTargetServerAndInstallAgentJar(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final String agentSha256 = resolveEmbeddedAgentSha256();
        final LifecycleServerProvider provider = createProvider(tempDirectory, true, true, agentSha256);

        // execute
        provider.prepareServer();

        // verify
        assertThat(provider.createBaseServerJarInvocations()).isEqualTo(1);
        assertThat(provider.createBaseServerInvocations()).isEqualTo(1);
        assertThat(provider.targetJarFilePath()).isRegularFile();
        assertThat(provider.targetServerDirectoryPath().resolve("plugins").resolve(LightkeeperEmbeddedAgent.FILE_NAME))
            .isRegularFile();
        assertThat(provider.targetServerDirectoryPath().resolve("server.properties")).isRegularFile();
    }

    @Test
    void prepareServer_shouldThrowExceptionWhenInstalledAgentHashDoesNotMatch(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final LifecycleServerProvider provider = createProvider(
            tempDirectory,
            true,
            true,
            "deadbeef"
        );

        // execute + verify
        assertThatThrownBy(provider::prepareServer)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("hash mismatch");
    }

    @Test
    void configureSpigotWatchdogTimeout_shouldCreateSettingsSectionWhenMissing(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final LifecycleServerProvider provider = createProviderWithoutAgent(tempDirectory);
        final Path spigotConfiguration = provider.baseServerDirectoryForTests().resolve("spigot.yml");
        Files.createDirectories(spigotConfiguration.getParent());
        Files.writeString(spigotConfiguration, "world-settings:\n  default:\n    verbose: false\n");

        // execute
        provider.configureSpigotWatchdogTimeoutForTests();

        // verify
        assertThat(Files.readAllLines(spigotConfiguration))
            .contains("settings:")
            .contains("  timeout-time: 600");
    }

    @Test
    void shouldBeRecreated_shouldReturnFalseForFreshExistingFile(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final LifecycleServerProvider provider = createProviderWithoutAgent(tempDirectory);
        final Path markerFile = Files.writeString(tempDirectory.resolve("fresh-file.txt"), "fresh");

        // execute
        final boolean shouldRecreate = provider.shouldBeRecreatedForTests(false, 365, markerFile);

        // verify
        assertThat(shouldRecreate).isFalse();
    }

    private static LifecycleServerProvider createProviderWithoutAgent(Path tempDirectory)
        throws Exception
    {
        return createProvider(tempDirectory, false, false, resolveEmbeddedAgentSha256());
    }

    private static LifecycleServerProvider createProvider(
        Path tempDirectory,
        boolean forceRebuildJar,
        boolean forceRecreateBaseServer,
        String agentJarSha256)
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
            forceRebuildJar,
            30,
            forceRecreateBaseServer,
            false,
            5,
            5,
            2,
            512,
            "java",
            null,
            "cache-key",
            "LightKeeper/Tests",
            agentJarSha256,
            "test-token",
            1,
            "agent-cache-id"
        );
        return new LifecycleServerProvider(log, specification);
    }

    private static String resolveEmbeddedAgentSha256()
        throws Exception
    {
        try (var embeddedAgentStream = LightkeeperEmbeddedAgent.openStream())
        {
            return HashUtil.sha256(embeddedAgentStream);
        }
    }

    private static final class LifecycleServerProvider extends ServerProvider
    {
        private int createBaseServerJarInvocations;
        private int createBaseServerInvocations;

        private LifecycleServerProvider(Log log, ServerSpecification serverSpecification)
        {
            super(log, "test", serverSpecification);
        }

        @Override
        protected void createBaseServerJar()
            throws MojoExecutionException
        {
            createBaseServerJarInvocations++;
            try
            {
                Files.createDirectories(jarCacheFile().getParent());
                Files.writeString(jarCacheFile(), "server-jar");
            }
            catch (IOException exception)
            {
                throw new MojoExecutionException("Failed to create cached jar in test.", exception);
            }
        }

        @Override
        protected void createBaseServer()
            throws MojoExecutionException
        {
            createBaseServerInvocations++;
            acceptEula();
            writeServerProperties(createDefaultServerProperties());
        }

        private int createBaseServerJarInvocations()
        {
            return createBaseServerJarInvocations;
        }

        private int createBaseServerInvocations()
        {
            return createBaseServerInvocations;
        }

        private Path baseServerDirectoryForTests()
        {
            return baseServerDirectory();
        }

        private void configureSpigotWatchdogTimeoutForTests()
            throws MojoExecutionException
        {
            configureSpigotWatchdogTimeout();
        }

        private boolean shouldBeRecreatedForTests(boolean forceRecreate, int expiryDays, Path file)
        {
            return shouldBeRecreated(forceRecreate, expiryDays, file);
        }
    }
}
