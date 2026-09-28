package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.Lightkeeper;
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
        try (ILightkeeperFramework framework = Lightkeeper.start(LightkeeperProvisioningIT.class))
        {
            // execute
            final Path serverDirectory = framework.server().directory();

            // verify
            assertThat(serverDirectory.resolve("lightkeeper-fixture-world/fixtures/marker.txt"))
                .isRegularFile()
                .hasContent("fixture-marker\n");
            assertThat(serverDirectory.resolve("plugins/lightkeeper-agent-spigot.jar")).isRegularFile();
            assertThat(serverDirectory.resolve("plugins/lightkeeper-spigot-test-plugin.jar")).isRegularFile();
            assertThat(serverDirectory.resolve("plugins/lightkeeper-spigot-test-plugin/test-overlay.yml"))
                .isRegularFile()
                .hasContent("overlay: true\n");
        }
    }
}
