package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;


@ExtendWith(LightkeeperExtension.class)
@FreshServer
class LightkeeperFreshServerIT
{
    @Test
    void mainWorld_shouldStartFreshServerWhenClassIsAnnotatedWithFreshServer(ILightkeeperFramework framework)
    {
        // execute
        final var mainWorld = framework.mainWorld();

        // verify
        LightkeeperAssertions.assertThat(mainWorld.name()).isNotBlank();
    }
}
