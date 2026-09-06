package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.LightkeeperRuntimeResolver;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;


class LightkeeperProvisioningIT
{
    @Test
    void prepareServer_shouldInstallConfiguredWorldAndOverlay()
        throws Exception
    {
        // setup
        final Path runtimeManifestPath = LightkeeperRuntimeResolver.resolve(LightkeeperProvisioningIT.class);

        // execute
        final RuntimeManifest runtimeManifest = new RuntimeManifestReader().read(runtimeManifestPath);
        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final String expectedServerType = System.getProperty("lightkeeper.expectedServerType", "paper");

        // verify
        assertThat(serverDirectory.resolve("lightkeeper-fixture-world/fixtures/marker.txt"))
            .isRegularFile()
            .hasContent("fixture-marker\n");
        assertThat(serverDirectory.resolve("plugins/lightkeeper-agent-spigot.jar")).isRegularFile();
        assertThat(serverDirectory.resolve("plugins/lightkeeper-spigot-test-plugin.jar")).isRegularFile();
        assertThat(serverDirectory.resolve("plugins/lightkeeper-spigot-test-plugin/test-overlay.yml"))
            .isRegularFile()
            .hasContent("overlay: true\n");
        assertThat(runtimeManifest.provisionedWorlds()).noneMatch(RuntimeManifest.ProvisionedWorld::loadOnStartup);
        assertThat(runtimeManifest.serverType()).isEqualTo(expectedServerType);
    }
}
