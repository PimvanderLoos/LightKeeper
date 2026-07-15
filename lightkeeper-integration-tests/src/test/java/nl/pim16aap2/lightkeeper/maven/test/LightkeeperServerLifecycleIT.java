package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
@FreshServer
class LightkeeperServerLifecycleIT
{
    @Test
    void serverLifecycle_shouldSupportGracefulStopStartAndRestart(ILightkeeperFramework framework)
    {
        // setup
        final Path serverDirectory = framework.server().directory();
        assertThat(serverDirectory).isDirectory();
        assertThat(framework.server().pluginDataDirectory("LightkeeperSpigotTestPlugin"))
            .isEqualTo(serverDirectory.resolve("plugins").resolve("LightkeeperSpigotTestPlugin"));
        framework.bots().join("lklife001", framework.worlds().main());

        // execute: graceful stop
        framework.server().stop();

        // verify: while stopped, the server directory remains accessible for seeding and inspection
        assertThat(serverDirectory.resolve("server.properties")).exists();

        // execute: start again after the graceful stop
        framework.server().start();

        // verify: the started server is fully usable
        assertThat(framework.worlds().main()).hasNonBlankName();
        assertThat(framework.bots().join("lklife002", framework.worlds().main())).hasName("lklife002");

        // execute: graceful restart while the server is running
        framework.server().restart();

        // verify: usable again after the restart
        assertThat(framework.worlds().main()).hasNonBlankName();
        assertThat(framework.bots().join("lklife003", framework.worlds().main())).hasName("lklife003");
    }
}
