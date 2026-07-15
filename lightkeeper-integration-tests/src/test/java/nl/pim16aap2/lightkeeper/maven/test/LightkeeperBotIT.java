package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.InteractionResult;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import nl.pim16aap2.lightkeeper.protocol.DropResult;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperBotIT
{
    private static final String TEST_GUI_PERMISSION = "lightkeeper.testplugin.lktestgui";

    @Test
    void playerBots_shouldExecuteCommandsAndInteractWithMenusAndBlocks(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.worlds().main();

        final var player = framework.bots().join("lkplayer001", world);
        final var secondPlayer = framework.bots().builder()
            .withName("lkplayer002")
            .atLocation(world, 0, 100, 0)
            .withHealth(10)
            .withPermissions(
                "minecraft.command.gamemode",
                "minecraft.command.time",
                "minecraft.command.weather",
                TEST_GUI_PERMISSION
            )
            .build();

        final var thirdPlayer = framework.bots().builder()
            .withRandomName()
            .atSpawn(world)
            .build();

        // execute
        secondPlayer.executeCommand("lktestgui")
            .andWaitForMenuOpen(10)
            .verifyMenuName("Main Menu")
            .clickItem("Button 1")
            .andWaitTicks(5)
            .verifyMenuName("Sub Menu")
            .dragWithMaterial("minecraft:stone", 3, 4, 5)
            .andWaitTicks(1)
            .verifyMenuName("Sub Menu");

        final MenuHandle menu = secondPlayer.getMenu();
        assertThat(menu)
            .isNotNull()
            .hasTitle("Sub Menu")
            .hasItemAt(0, "minecraft:barrier")
            .hasItemAt(3, "minecraft:stone")
            .hasItemAt(2, "minecraft:diamond_sword");

        menu.clickAtIndex(2)
            .andWaitForMenuClose()
            .verifyMenuClosed();

        player.placeBlock("minecraft:stone", 1, 100, 0)
            .andWaitTicks(1);
        world.setBlockAt(new BlockPos(3, 100, 0), "minecraft:gold_block");
        final InteractionResult leftClickResult = player.leftClickBlock(new BlockPos(3, 100, 0), BlockFace.NORTH);
        final InteractionResult rightClickResult = player.rightClickBlock(new BlockPos(3, 100, 0), BlockFace.SOUTH);
        player.andWaitTicks(1);
        final DropResult emptyHandDropResult = player.dropMainHandItem();

        // verify
        assertThat(leftClickResult).isEqualTo(new InteractionResult(true, false));
        assertThat(rightClickResult).isEqualTo(new InteractionResult(true, false));
        assertThat(emptyHandDropResult).isEqualTo(DropResult.EMPTY_HAND);
        assertThat(world)
            .hasBlockAt(1, 100, 0)
            .ofType(Material.STONE);
        assertThat(world)
            .hasBlockAt(3, 100, 0)
            .ofType(Material.GOLD_BLOCK);
        assertThat(player)
            .receivedMessage("LK_BLOCK_CLICK action=LEFT_CLICK_BLOCK block=minecraft:gold_block x=3 y=100 z=0");
        assertThat(player)
            .receivedMessage("LK_BLOCK_CLICK action=RIGHT_CLICK_BLOCK block=minecraft:gold_block x=3 y=100 z=0");
        assertThat(secondPlayer)
            .receivedMessage("You clicked Button 1");
        assertThat(secondPlayer)
            .receivedMessagesText()
            .contains("clicked")
            .contains("You");

        // Keep explicit use so this path is covered in integration tests.
        assertThat(thirdPlayer.name()).isNotBlank();
    }

    @Test
    void frameworkApi_shouldCoverBuilderAndCommandAndHandleVariants(ILightkeeperFramework framework)
    {
        // setup
        final var buildWorldResult = framework.worlds().builder()
            .withName("lk_world_" + UUID.randomUUID().toString().replace("-", ""))
            .withSeed(123L)
            .build();
        final var consoleCommandResult = framework.server().executeCommand(CommandSource.CONSOLE, "time set day");
        final var builtPlayer = framework.bots().builder()
            .withName("lkplayer003")
            .atSpawn(buildWorldResult)
            .withPermissions(TEST_GUI_PERMISSION)
            .build();
        final UUID explicitUuid = UUID.fromString("1479d27d-1260-48f5-8044-b6aad4c8ea0f");
        final var explicitPlayer = framework.bots().join("lkplayer004", explicitUuid, buildWorldResult);

        // execute
        builtPlayer.executeCommand("lktestgui");
        final MenuHandle menu = builtPlayer.andWaitForMenuOpen()
            .verifyMenuName("Main Menu")
            .clickAtIndex(0)
            .andWaitTicks(5)
            .verifyMenuName("Sub Menu");
        final ItemStack expectedSword = mock(ItemStack.class);
        when(expectedSword.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(expectedSword.getItemMeta()).thenReturn(null);
        final boolean hasSword = menu.hasItemAt(2, expectedSword);
        final boolean menuOpenBeforeClose = menu.snapshot().open();
        menu.clickAtIndex(2)
            .andWaitForMenuClose(Duration.ofSeconds(10))
            .verifyMenuClosed();
        builtPlayer.andWaitTicks(1);
        final MenuHandle noLongerOpenMenu = builtPlayer.getMenu();
        explicitPlayer.placeBlock("STONE", 2, 100, 0);

        // verify
        eventually(Duration.ofSeconds(20), () ->
            assertThat(buildWorldResult).hasBlockAt(new BlockPos(2, 100, 0)).ofType(Material.STONE));
        assertThat(consoleCommandResult.success()).isTrue();
        assertThat(buildWorldResult.name()).isNotBlank();
        assertThat(menu.player()).isEqualTo(builtPlayer);
        assertThat(menuOpenBeforeClose).isTrue();
        assertThat(hasSword).isTrue();
        assertThat(noLongerOpenMenu).isNull();
        assertThat(framework)
            .serverOutput()
            .isNotEmpty();

        explicitPlayer.remove();
        builtPlayer.remove();
    }

    @Test
    void lktestgui_shouldFailWhenPlayerHasNoPermission(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.worlds().main();
        final var playerWithoutPermission = framework.bots().builder()
            .withName("lkplayer005")
            .atSpawn(world)
            .build();

        // execute
        playerWithoutPermission.executeCommand("lktestgui")
            .andWaitTicks(5);
        final MenuHandle menu = playerWithoutPermission.getMenu();

        // verify
        assertThat(menu).isNull();
        assertThat(playerWithoutPermission)
            .receivedMessage("You do not have permission to use /lktestgui.");
    }

    @Test
    void runtimeActions_shouldTeleportManageChunksAndCaptureEvents(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.worlds().main();
        final var player = framework.bots().builder()
            .withRandomName()
            .atLocation(world, 0, 100, 0)
            .build();
        final int chunkX = 24;
        final int chunkZ = 24;

        // execute
        try (var eventCapture = framework.events().capture("org.bukkit.event.player.PlayerTeleportEvent"))
        {
            world.loadChunk(chunkX, chunkZ);
            final boolean loadedAfterLoad = world.isChunkLoaded(chunkX, chunkZ);
            player.teleport(world, 1, 100, 1);
            framework.waitUntil(
                () -> !eventCapture.getCapturedEvents().isEmpty(),
                Duration.ofSeconds(10)
            );

            // verify
            assertThat(loadedAfterLoad).isTrue();
            final var capturedEvents = eventCapture.getCapturedEvents();
            assertThat(capturedEvents).isNotEmpty();
            // The typed envelope carries the acting player as a reference, not a stringified object.
            assertThat(capturedEvents.getFirst().value("getPlayer"))
                .isInstanceOfSatisfying(IProtocolValue.PRef.class, playerRef ->
                    assertThat(playerRef.id()).isEqualTo(player.uniqueId().toString()));
        }
    }
}
