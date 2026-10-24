package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.*;

/**
 * Validates plugin, effective-permission, and menu assertions against both provisioned server platforms.
 */
@ExtendWith(LightkeeperExtension.class)
class LightkeeperAssertionBasicsIT
{
    /** Fixture plugin installed by the integration-test provisioner. */
    private static final String FIXTURE_PLUGIN = "LightkeeperSpigotTestPlugin";

    /** Permission controlling the fixture GUI command. */
    private static final String GUI_PERMISSION = "lightkeeper.testplugin.lktestgui";

    @Test
    void pluginAssertions_shouldDistinguishMissingEnabledAndWrongVersion(ILightkeeperFramework framework)
    {
        // setup
        final var server = assertThat(framework).server();

        // execute
        final var installed = server.plugin(FIXTURE_PLUGIN);
        final var missing = server.plugin("LightkeeperMissingAssertionFixture");

        // verify
        installed
            .isPresent()
            .isEnabled();

        missing.isAbsent();

        assertThatThrownBy(missing::isDisabled)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("not present");

        assertThatThrownBy(installed::isDisabled)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("was enabled");

        assertThatThrownBy(() -> installed.hasVersion("deliberately-wrong-version"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll(FIXTURE_PLUGIN, "deliberately-wrong-version", "version was");
    }

    @Test
    void permissionAssertions_shouldObserveRuntimeChangesOnRetainedSubject(ILightkeeperFramework framework)
    {
        // setup
        final var player = framework.bots().join("lkassertperm", framework.worlds().main());
        final var assertion = assertThat(player);

        // execute
        player.permissions().grant(GUI_PERMISSION);

        // verify
        assertion.hasPermission(GUI_PERMISSION);

        // execute
        player.permissions().revoke(GUI_PERMISSION);

        // verify
        assertion.doesNotHavePermission(GUI_PERMISSION);

        // execute
        player.permissions().unset(GUI_PERMISSION);

        // verify
        assertion.doesNotHavePermission(GUI_PERMISSION);
    }

    @Test
    void menuAssertions_shouldObserveOpeningAndClosingOnRetainedSubjects(ILightkeeperFramework framework)
    {
        // setup
        final var player = framework.bots().builder()
            .withName("lkassertmenu")
            .atSpawn(framework.worlds().main())
            .withPermissions(GUI_PERMISSION)
            .build();

        final var playerAssertion = assertThat(player);
        playerAssertion.doesNotHaveOpenMenu();

        // execute
        final var menu = player.executeCommand("lktestgui").andWaitForMenuOpen();
        final var menuAssertion = assertThat(menu);

        // verify
        playerAssertion.hasOpenMenu();
        menuAssertion.isOpen().hasTitle("Main Menu");

        // execute
        menu.clickAtIndex(0)
            .andWaitTicks(1)
            .clickAtIndex(2)
            .andWaitForMenuClose();

        // verify
        menuAssertion.isClosed();
        playerAssertion.doesNotHaveOpenMenu();
    }
}
