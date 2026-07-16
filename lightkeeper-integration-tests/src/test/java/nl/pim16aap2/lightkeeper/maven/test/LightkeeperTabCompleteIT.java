package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperTabCompleteIT
{
    private static final String TAB_PERMISSION = "lightkeeper.testplugin.lktestperm";

    @Test
    void tabComplete_shouldSuggestPermissionlessPluginCommandName(ILightkeeperFramework framework)
    {
        // setup
        final WorldHandle world = framework.worlds().main();
        final PlayerHandle player = framework.bots().join("lktab001", world);

        // execute — lktestlocale declares no permission, so it is visible to any player
        final List<String> completions = player.tabComplete("/lktestlocal");

        // verify — a player sender's completions carry a leading slash, so match on the substring
        assertThat(completions).anyMatch(completion -> completion.contains("lktestlocale"));
    }

    @Test
    void tabComplete_shouldReturnEmptyListForUnknownCommand(ILightkeeperFramework framework)
    {
        // setup
        final WorldHandle world = framework.worlds().main();
        final PlayerHandle player = framework.bots().join("lktab002", world);

        // execute — the trailing token triggers the args-completion branch, whose unknown-command lookup is null
        final List<String> completions = player.tabComplete("/zzznocommand arg");

        // verify — a null command-map result is normalized to an empty list rather than propagating
        assertThat(completions).isEmpty();
    }

    @Test
    void tabComplete_shouldFilterCommandNamesByPlayerPermission(ILightkeeperFramework framework)
    {
        // setup — lktestperm carries a command-level permission (lightkeeper.testplugin.lktestperm) that
        // defaults to false, so the command map filters it out of completions until the player is granted it
        final WorldHandle world = framework.worlds().main();
        final PlayerHandle player = framework.bots().join("lktab003", world);

        // execute + verify — without the permission the command is filtered out of the completions
        final List<String> beforeGrant = player.tabComplete("/lktestp");
        assertThat(beforeGrant).noneMatch(completion -> completion.contains("lktestperm"));

        // execute + verify — granting the permission makes the same buffer offer the command
        player.permissions().grant(TAB_PERMISSION);
        final List<String> afterGrant = player.tabComplete("/lktestp");
        assertThat(afterGrant).anyMatch(completion -> completion.contains("lktestperm"));
    }
}
