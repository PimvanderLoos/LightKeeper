package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LightkeeperProvisioningIT
{
    @Test
    void prepareServer_shouldInstallConfiguredWorldAndOverlay()
        throws Exception
    {
        // setup
        final String runtimeManifestProperty = System.getProperty("lightkeeper.runtimeManifestPath");
        assertThat(runtimeManifestProperty).isNotBlank();
        final Path runtimeManifestPath = Path.of(runtimeManifestProperty);

        // execute
        final RuntimeManifest runtimeManifest = new RuntimeManifestReader().read(runtimeManifestPath);
        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final String expectedServerType = System.getProperty("lightkeeper.expectedServerType", "paper");

        // verify
        assertThat(serverDirectory.resolve("lightkeeper-fixture-world/fixtures/marker.txt"))
            .isRegularFile()
            .hasContent("fixture-marker\n");
        assertThat(serverDirectory.resolve("plugins/lightkeeper-agent-paper/test-overlay.yml"))
            .isRegularFile()
            .hasContent("overlay: true\n");
        assertThat(runtimeManifest.preloadedWorlds()).isEmpty();
        assertThat(runtimeManifest.serverType()).isEqualTo(expectedServerType);
    }
}
