package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
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

        // execute + verify: the default-false node is not granted at spawn and the gated command is denied
        assertThat(permissions.has(TEST_GUI_PERMISSION)).isFalse();
        player.executeCommand("lktestgui").andWaitTicks(1);
        assertThat(player).receivedMessage(NO_PERMISSION_MESSAGE);
        assertThat(player.getMenu()).isNull();

        // execute + verify: a runtime grant opens the gate
        permissions.grant(TEST_GUI_PERMISSION);
        assertThat(permissions.has(TEST_GUI_PERMISSION)).isTrue();
        player.executeCommand("lktestgui")
            .andWaitForMenuOpen()
            .verifyMenuName("Main Menu");

        // execute + verify: a runtime revoke closes the gate again
        permissions.revoke(TEST_GUI_PERMISSION);
        assertThat(permissions.has(TEST_GUI_PERMISSION)).isFalse();

        // execute + verify: unset restores the node's default (false), and a fresh grant still works after it
        permissions.unset(TEST_GUI_PERMISSION);
        assertThat(permissions.has(TEST_GUI_PERMISSION)).isFalse();
        permissions.grant(TEST_GUI_PERMISSION);
        assertThat(permissions.has(TEST_GUI_PERMISSION)).isTrue();
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

        // execute + verify: the spawn-time grant is visible to live queries
        assertThat(player.permissions().has(TEST_GUI_PERMISSION)).isTrue();

        // execute + verify: a runtime revoke overrides the spawn-time grant on the same attachment
        player.permissions().revoke(TEST_GUI_PERMISSION);
        assertThat(player.permissions().has(TEST_GUI_PERMISSION)).isFalse();

        // execute + verify: unset removes the node entirely, restoring the default (false)
        player.permissions().unset(TEST_GUI_PERMISSION);
        assertThat(player.permissions().has(TEST_GUI_PERMISSION)).isFalse();
    }
}
