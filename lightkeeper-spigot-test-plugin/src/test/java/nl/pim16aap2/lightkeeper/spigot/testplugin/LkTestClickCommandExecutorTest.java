package nl.pim16aap2.lightkeeper.spigot.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LkTestClickCommandExecutorTest
{
    @Test
    void onCommand_shouldSendConfirmMessageWhenArgIsConfirm()
    {
        // setup
        final LkTestClickCommandExecutor executor = new LkTestClickCommandExecutor();
        final Player player = mock();
        final Command command = mock();

        // execute
        final boolean handled = executor.onCommand(player, command, "lktestclick", new String[]{"confirm"});

        // verify
        assertThat(handled).isTrue();
        verify(player).sendMessage(LkTestClickCommandExecutor.CONFIRM_MESSAGE);
    }

    @Test
    void onCommand_shouldReplyPlayersOnlyForNonPlayerSender()
    {
        // setup
        final LkTestClickCommandExecutor executor = new LkTestClickCommandExecutor();
        final CommandSender sender = mock();
        final Command command = mock();

        // execute
        final boolean handled = executor.onCommand(sender, command, "lktestclick", new String[0]);

        // verify
        assertThat(handled).isTrue();
        verify(sender).sendMessage(LkTestClickCommandExecutor.PLAYERS_ONLY_MESSAGE);
    }

    @Test
    void onCommand_shouldDispatchTellrawWithRunCommandClickEventWhenNoArgs()
    {
        // setup
        final LkTestClickCommandExecutor executor = new LkTestClickCommandExecutor();
        final Player player = mock();
        when(player.getName()).thenReturn("lkclick01");
        final Command command = mock();
        final ConsoleCommandSender console = mock();
        final ArgumentCaptor<String> dispatched = ArgumentCaptor.forClass(String.class);

        // execute
        final boolean handled;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            handled = executor.onCommand(player, command, "lktestclick", new String[0]);
            bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), dispatched.capture()));
        }

        // verify — a /tellraw that targets the player and carries a run_command click_event on the wire key
        assertThat(handled).isTrue();
        assertThat(dispatched.getValue())
            .startsWith("minecraft:tellraw lkclick01 ")
            .contains(LkTestClickCommandExecutor.CLICK_TEXT)
            .contains("\"click_event\":{\"action\":\"run_command\"")
            .contains("\"command\":\"/lktestclick confirm\"");
    }
}
