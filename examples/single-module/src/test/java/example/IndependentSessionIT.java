package example;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.*;

/** An independent class cannot observe another class's modified world. */
@ExtendWith(LightkeeperExtension.class)
class IndependentSessionIT
{
    @Test
    void mainWorld_shouldStartFromCleanPreparedInputs(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.worlds().main();

        // execute
        final String material = world.blockTypeAt(new BlockPos(0, 240, 0));

        // verify
        assertThat(material).isNotEqualTo("minecraft:diamond_block");
        System.out.println("SESSION_PROOF independent-class-clean-world=true");
    }
}
