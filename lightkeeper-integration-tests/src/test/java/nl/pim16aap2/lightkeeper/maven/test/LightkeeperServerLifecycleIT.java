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
        final Path serverDirectory = framework.serverDirectory();
        assertThat(serverDirectory).isDirectory();
        assertThat(framework.pluginDataDirectory("LightkeeperSpigotTestPlugin"))
            .isEqualTo(serverDirectory.resolve("plugins").resolve("LightkeeperSpigotTestPlugin"));
        framework.createPlayer("lklife001", framework.mainWorld());

        // execute: graceful stop
        framework.stopServer();

        // verify: while stopped, the server directory remains accessible for seeding and inspection
        assertThat(serverDirectory.resolve("server.properties")).exists();

        // execute: start again after the graceful stop
        framework.startServer();

        // verify: the started server is fully usable
        assertThat(framework.mainWorld()).hasNonBlankName();
        assertThat(framework.createPlayer("lklife002", framework.mainWorld())).hasName("lklife002");

        // execute: graceful restart while the server is running
        framework.restartServer();

        // verify: usable again after the restart
        assertThat(framework.mainWorld()).hasNonBlankName();
        assertThat(framework.createPlayer("lklife003", framework.mainWorld())).hasName("lklife003");
    }
}
