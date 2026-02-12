package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.LightkeeperFramework;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
@FreshServer
class LightkeeperFreshServerIT
{
    @Test
    void mainWorld_shouldStartFreshServerWhenClassIsAnnotatedWithFreshServer(LightkeeperFramework framework)
    {
        // setup

        // execute
        final var mainWorld = framework.mainWorld();

        // verify
        assertThat(mainWorld.name()).isNotBlank();
    }
}
