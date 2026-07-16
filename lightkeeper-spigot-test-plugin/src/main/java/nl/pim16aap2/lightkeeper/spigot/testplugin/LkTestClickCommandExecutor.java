package nl.pim16aap2.lightkeeper.spigot.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles {@code /lktestclick} command execution for the standalone test plugin.
 *
 * <p>Invoked with no arguments, it sends the invoking player a chat component carrying a {@code run_command}
 * click event, so integration tests can capture the component, extract the command, and "click" it. Invoked with
 * {@code confirm} (the command the click event runs), it replies with a deterministic marker the test can assert
 * on to prove the click actually dispatched the command.
 *
 * <p>The clickable component is produced through the vanilla {@code /tellraw} command rather than an API builder:
 * this Minecraft version's spigot-api ships no chat-component builder (the BungeeCord chat API was removed and
 * Adventure is Paper-only), and {@code /tellraw} is parsed by the same native component codec that serializes the
 * {@code click_event} field on the wire, so the round trip is faithful and identical on Spigot and Paper.
 */
final class LkTestClickCommandExecutor implements CommandExecutor
{
    /**
     * Visible text of the clickable component; integration tests locate the component by this marker.
     */
    static final String CLICK_TEXT = "LK_CLICK_ME";
    /**
     * Argument that triggers the confirmation reply instead of emitting a new clickable component.
     */
    static final String CONFIRM_ARG = "confirm";
    /**
     * Command the clickable component runs when clicked (a self-invocation with {@link #CONFIRM_ARG}).
     */
    static final String RUN_COMMAND = "/lktestclick " + CONFIRM_ARG;
    /**
     * Deterministic reply sent once the {@link #RUN_COMMAND} is executed, asserted on by integration tests.
     */
    static final String CONFIRM_MESSAGE = "LK_TEST_CLICK confirmed";
    /**
     * Reply sent when the command is invoked by a non-player sender (only players receive chat components).
     */
    static final String PLAYERS_ONLY_MESSAGE = "Only players can use /lktestclick.";

    /**
     * Emits a clickable component to the player, or replies with the confirmation marker on {@code confirm}.
     *
     * @param sender
     *     Sender that invoked the command.
     * @param command
     *     Command metadata.
     * @param label
     *     Used command label.
     * @param args
     *     Command arguments; {@code confirm} selects the confirmation reply.
     * @return
     *     Always {@code true} because this executor handles all command outcomes directly.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player player))
        {
            sender.sendMessage(PLAYERS_ONLY_MESSAGE);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase(CONFIRM_ARG))
        {
            player.sendMessage(CONFIRM_MESSAGE);
            return true;
        }

        final String tellraw = ("minecraft:tellraw %s "
            + "{\"text\":\"%s\",\"click_event\":{\"action\":\"run_command\",\"command\":\"%s\"}}")
            .formatted(player.getName(), CLICK_TEXT, RUN_COMMAND);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), tellraw);
        return true;
    }
}
