package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.PermissionControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperPermissionsIT
{
    private static final String TEST_GUI_PERMISSION = "lightkeeper.testplugin.lktestgui";
    private static final String NO_PERMISSION_MESSAGE = "You do not have permission to use /lktestgui.";

    @Test
    void permissions_shouldGateCommandsThroughRuntimeGrantAndRevoke(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.mainWorld();
        final var player = framework.createPlayer("lkperm001", world);
        final PermissionControl permissions = player.permissions();

        // execute
        final boolean hasNodeAtSpawn = permissions.has(TEST_GUI_PERMISSION);
        player.executeCommand("lktestgui").andWaitTicks(1);
        final MenuHandle menuWithoutPermission = player.getMenu();

        permissions.grant(TEST_GUI_PERMISSION);
        final boolean hasNodeAfterGrant = permissions.has(TEST_GUI_PERMISSION);
        player.executeCommand("lktestgui")
            .andWaitForMenuOpen()
            .verifyMenuName("Main Menu");

        permissions.revoke(TEST_GUI_PERMISSION);
        final boolean hasNodeAfterRevoke = permissions.has(TEST_GUI_PERMISSION);

        permissions.unset(TEST_GUI_PERMISSION);
        final boolean hasNodeAfterUnset = permissions.has(TEST_GUI_PERMISSION);

        permissions.grant(TEST_GUI_PERMISSION);
        final boolean hasNodeAfterRegrant = permissions.has(TEST_GUI_PERMISSION);

        // verify
        assertThat(hasNodeAtSpawn).isFalse();
        assertThat(player).receivedMessage(NO_PERMISSION_MESSAGE);
        assertThat(menuWithoutPermission).isNull();
        assertThat(hasNodeAfterGrant).isTrue();
        assertThat(hasNodeAfterRevoke).isFalse();
        assertThat(hasNodeAfterUnset).isFalse();
        assertThat(hasNodeAfterRegrant).isTrue();
    }

    @Test
    void permissions_shouldOverrideSpawnTimePermissions(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.mainWorld();
        final var player = framework.buildPlayer()
            .withName("lkperm002")
            .atSpawn(world)
            .withPermissions(TEST_GUI_PERMISSION)
            .build();

        // execute
        final boolean hasSpawnTimeGrant = player.permissions().has(TEST_GUI_PERMISSION);
        player.permissions().revoke(TEST_GUI_PERMISSION);
        final boolean hasAfterRevoke = player.permissions().has(TEST_GUI_PERMISSION);
        player.permissions().unset(TEST_GUI_PERMISSION);
        final boolean hasAfterUnset = player.permissions().has(TEST_GUI_PERMISSION);

        // verify
        assertThat(hasSpawnTimeGrant).isTrue();
        assertThat(hasAfterRevoke).isFalse();
        assertThat(hasAfterUnset).isFalse();
    }
}
