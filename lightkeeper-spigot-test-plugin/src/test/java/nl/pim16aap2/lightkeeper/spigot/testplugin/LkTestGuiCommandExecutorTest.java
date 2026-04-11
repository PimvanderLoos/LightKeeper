package nl.pim16aap2.lightkeeper.spigot.testplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LkTestGuiCommandExecutorTest
{
    @Test
    void onCommand_shouldRejectNonPlayerSender()
    {
        // setup
        final GuiMenuService guiMenuService = mock();
        final LkTestGuiCommandExecutor executor = new LkTestGuiCommandExecutor(guiMenuService);
        final CommandSender sender = mock();
        final Command command = mock();

        // execute
        final boolean handled = executor.onCommand(sender, command, "lktestgui", new String[0]);

        // verify
        assertThat(handled).isTrue();
        verify(sender).sendMessage(LkTestGuiCommandExecutor.NON_PLAYER_MESSAGE);
        verifyNoInteractions(guiMenuService);
    }

    @Test
    void onCommand_shouldRejectPlayerWithoutPermission()
    {
        // setup
        final GuiMenuService guiMenuService = mock();
        final LkTestGuiCommandExecutor executor = new LkTestGuiCommandExecutor(guiMenuService);
        final Player player = mock();
        final Command command = mock();
        when(player.hasPermission(LkTestGuiCommandExecutor.COMMAND_PERMISSION)).thenReturn(false);

        // execute
        final boolean handled = executor.onCommand(player, command, "lktestgui", new String[0]);

        // verify
        assertThat(handled).isTrue();
        verify(player).sendMessage(LkTestGuiCommandExecutor.NO_PERMISSION_MESSAGE);
        verifyNoInteractions(guiMenuService);
    }

    @Test
    void onCommand_shouldOpenMainMenuForAuthorizedPlayer()
    {
        // setup
        final GuiMenuService guiMenuService = mock();
        final LkTestGuiCommandExecutor executor = new LkTestGuiCommandExecutor(guiMenuService);
        final Player player = mock();
        final Command command = mock();
        when(player.hasPermission(LkTestGuiCommandExecutor.COMMAND_PERMISSION)).thenReturn(true);

        // execute
        final boolean handled = executor.onCommand(player, command, "lktestgui", new String[0]);

        // verify
        assertThat(handled).isTrue();
        verify(guiMenuService).openMainMenu(player);
    }
}
