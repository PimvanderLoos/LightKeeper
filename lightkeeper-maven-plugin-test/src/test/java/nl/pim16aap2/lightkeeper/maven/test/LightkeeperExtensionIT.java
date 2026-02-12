package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.LightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperExtensionIT
{
    @Test
    void mainWorld_shouldInjectFrameworkFromExtension(LightkeeperFramework framework)
    {
        // setup

        // execute
        final var mainWorld = framework.mainWorld();

        // verify
        assertThat(mainWorld.name()).isNotBlank();
    }

    @Test
    void newWorld_shouldCreateIsolatedWorldWhenRequestedFromFramework(LightkeeperFramework framework)
    {
        // setup
        final var mainWorld = framework.mainWorld();
        final Vector3Di position = new Vector3Di(2, 70, 2);

        // execute
        final var world = framework.newWorld();
        framework.setBlock(world, position, "STONE");
        framework.waitUntil(() -> "STONE".equals(world.blockTypeAt(position)), Duration.ofSeconds(20));

        // verify
        assertThat(world.name()).isNotBlank();
        assertThat(world.name()).isNotEqualTo(mainWorld.name());
        assertThat(world.blockTypeAt(position)).isEqualTo("STONE");
    }
}
