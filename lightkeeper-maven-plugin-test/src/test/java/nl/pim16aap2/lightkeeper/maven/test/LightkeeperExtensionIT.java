package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.CommandSource;
import nl.pim16aap2.lightkeeper.framework.FreshWorld;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.LightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

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
    @FreshWorld
    void newWorld_shouldCreateIsolatedWorldWhenFreshWorldIsEnabled(LightkeeperFramework framework)
    {
        // setup
        final String worldName = "lk_extension_" + UUID.randomUUID().toString().replace("-", "");

        // execute
        final var world = framework.newWorld(new WorldSpec(
            worldName,
            WorldSpec.WorldType.NORMAL,
            WorldSpec.WorldEnvironment.NORMAL,
            0L
        ));
        framework.executeCommand(CommandSource.CONSOLE, "execute in %s run time set day".formatted(world.name()));

        // verify
        assertThat(world.name()).isEqualTo(worldName);
    }

}
