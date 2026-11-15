package example;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

/** A complete first integration test; no commands or plugins from LightKeeper's internal fixtures are needed. */
@ExtendWith(LightkeeperExtension.class)
class GettingStartedIT
{
    @Test
    void placeBlock_shouldUpdateWorld(ILightkeeperFramework framework)
    {
        // setup
        final WorldHandle world = framework.worlds().main();
        final PlayerHandle player = framework.bots().builder()
            .withName("lk_tester")
            .atSpawn(world)
            .build();

        // execute
        player.placeBlock("minecraft:stone", 1, 100, 0).andWaitTicks(1);

        // verify
        assertThat(world).hasBlockAt(1, 100, 0).ofType("minecraft:stone");
    }
}
