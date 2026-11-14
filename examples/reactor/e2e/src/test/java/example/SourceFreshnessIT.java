package example;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.*;

/** Tests deployed behavior; the optional expectation property is used only by the regression runner. */
@ExtendWith(LightkeeperExtension.class)
class SourceFreshnessIT
{
    @Test
    void packagedCommand_shouldReturnCurrentProductionRevision(ILightkeeperFramework framework)
    {
        // setup
        final var player = framework.bots().builder()
            .withName("lk_consumer")
            .atSpawn(framework.worlds().main())
            .build();

        // execute
        player.executeCommand("lkversion");

        // verify
        assertThat(framework.server().platform().name()).isEqualTo(System.getProperty("expected.platform", "PAPER"));
        assertThat(player).receivedMessage(System.getProperty("expected.version", "v1"));
    }
}
