package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.Lightkeeper;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

class LightkeeperFrameworkIT
{
    @Test
    void start_shouldExposeServerAndMainWorld()
        throws Exception
    {
        // setup
        try (ILightkeeperFramework framework = Lightkeeper.start(LightkeeperFrameworkIT.class))
        {
            // execute
            final WorldHandle worldHandle = framework.worlds().main();
            final String expectedServerType = System.getProperty("lightkeeper.expectedServerType");

            // verify
            if (expectedServerType == null)
                assertThat(framework.server().platform().name()).isIn("PAPER", "SPIGOT");
            else
                assertThat(framework.server().platform().name()).isEqualToIgnoringCase(expectedServerType);
            assertThat(framework.server().directory()).isDirectory();
            assertThat(worldHandle).hasNonBlankName();
        }
    }

    @Test
    void newWorld_shouldCreateWorldAndSetBlockWhenExecuteCommandIsUsed()
    {
        // setup
        final String worldName = "lk_world_" + UUID.randomUUID().toString().replace("-", "");
        final BlockPos position = new BlockPos(1, 70, 1);
        final WorldSpec worldSpec = new WorldSpec(
            worldName,
            WorldSpec.WorldType.FLAT,
            WorldSpec.WorldEnvironment.NORMAL,
            1234L
        );

        // execute
        try (ILightkeeperFramework framework = Lightkeeper.start(LightkeeperFrameworkIT.class))
        {
            final WorldHandle worldHandle = framework.worlds().create(worldSpec);
            worldHandle.setBlockAt(position, "STONE");
            framework.waitUntil(
                () -> "minecraft:stone".equals(worldHandle.blockTypeAt(position)),
                Duration.ofSeconds(20)
            );

            // verify
            assertThat(worldHandle)
                .hasNameEqualTo(worldName)
                .hasBlockAt(position)
                .ofType("STONE");
        }
    }
}
