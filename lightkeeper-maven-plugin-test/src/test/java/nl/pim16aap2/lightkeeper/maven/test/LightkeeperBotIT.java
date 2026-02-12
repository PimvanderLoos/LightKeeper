package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.LightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperBotIT
{
    @Test
    void playerBots_shouldExecuteCommandsAndInteractWithMenusAndBlocks(LightkeeperFramework framework)
    {
        // setup
        final var world = framework.mainWorld();

        final var player = framework.createPlayer("lkplayer001", world);
        final var secondPlayer = framework.buildPlayer()
            .withName("lkplayer002")
            .atLocation(world, 0, 100, 0)
            .withHealth(10)
            .withPermissions(
                "minecraft.command.gamemode",
                "minecraft.command.time",
                "minecraft.command.weather"
            )
            .build();

        final var thirdPlayer = framework.buildPlayer()
            .withRandomName()
            .atSpawn(world)
            .build();

        // execute
        secondPlayer.executeCommand("lktestgui")
            .andWaitForMenuOpen(10)
            .verifyMenuName("Main Menu")
            .clickAtIndex(0)
            .andWaitTicks(5)
            .verifyMenuName("Sub Menu")
            .dragWithMaterial("minecraft:stone", 3, 4, 5)
            .andWaitTicks(1)
            .verifyMenuName("Sub Menu");

        final MenuHandle menu = secondPlayer.getMenu();
        org.assertj.core.api.Assertions.assertThat(menu).isNotNull();
        assertThat(menu)
            .hasTitle("Sub Menu")
            .hasItemAt(0, "minecraft:barrier")
            .hasItemAt(3, "minecraft:stone")
            .hasItemAt(2, "minecraft:diamond_sword");

        menu.clickAtIndex(2)
            .andWaitForMenuClose()
            .verifyMenuClosed();

        player.placeBlock("minecraft:stone", 1, 100, 0)
            .andWaitTicks(1);

        // verify
        assertThat(world)
            .hasBlockAt(1, 100, 0)
            .ofType(Material.STONE);

        // Keep explicit use so this path is covered in integration tests.
        org.assertj.core.api.Assertions.assertThat(thirdPlayer.name()).isNotBlank();
    }
}
