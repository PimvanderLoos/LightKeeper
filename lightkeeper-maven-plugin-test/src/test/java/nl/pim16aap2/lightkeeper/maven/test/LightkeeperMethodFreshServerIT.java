package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperMethodFreshServerIT
{
    @Test
    @FreshServer
    void mainWorld_shouldStartFreshServerWhenMethodIsAnnotated(ILightkeeperFramework framework)
    {
        // execute
        final var mainWorld = framework.mainWorld();

        // verify
        assertThat(mainWorld.name()).isNotBlank();
    }
}
