package example;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.*;

/** Restarts the process per method while retaining the owning class's private world. */
@FreshServer
@ExtendWith(LightkeeperExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FreshSessionIT
{
    @Test
    @Order(1)
    void firstMethod_shouldWriteWorldState(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.worlds().main();
        final var location = new BlockPos(0, 240, 0);

        // execute
        world.setBlockAt(location, "minecraft:diamond_block");

        // verify
        assertThat(world.blockTypeAt(location)).isEqualTo("minecraft:diamond_block");
    }

    @Test
    @Order(2)
    void secondMethod_shouldKeepClassWorldStateAcrossFreshProcessAndRestart(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.worlds().main();
        final var location = new BlockPos(0, 240, 0);

        // execute
        final String afterFreshProcess = world.blockTypeAt(location);
        framework.server().restart();

        // verify
        assertThat(afterFreshProcess).isEqualTo("minecraft:diamond_block");
        assertThat(world.blockTypeAt(location)).isEqualTo("minecraft:diamond_block");
        System.out.println("SESSION_PROOF fresh-process-and-restart-retain-world=true");
    }
}
