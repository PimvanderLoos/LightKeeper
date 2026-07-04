package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperExtensionIT
{
    @Test
    void mainWorld_shouldInjectFrameworkFromExtension(ILightkeeperFramework framework)
    {
        // execute
        final var mainWorld = framework.mainWorld();

        // verify
        assertThat(mainWorld).hasNonBlankName();
    }

    @Test
    void newWorld_shouldCreateIsolatedWorldWhenRequestedFromFramework(ILightkeeperFramework framework)
    {
        // setup
        final var mainWorld = framework.mainWorld();
        final Vector3Di position = new Vector3Di(2, 70, 2);

        // execute
        final var world = framework.newWorld();
        world.setBlockAt(position, "STONE");
        framework.waitUntil(() -> "STONE".equals(world.blockTypeAt(position)), Duration.ofSeconds(20));

        // verify
        assertThat(world.name())
            .isNotBlank()
            .isNotEqualTo(mainWorld.name());
        assertThat(world)
            .hasBlockAt(position)
            .ofType("STONE");
    }
}
