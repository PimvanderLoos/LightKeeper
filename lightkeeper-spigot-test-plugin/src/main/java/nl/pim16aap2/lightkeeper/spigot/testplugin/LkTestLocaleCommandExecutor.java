package nl.pim16aap2.lightkeeper.spigot.testplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Handles {@code /lktestlocale} command execution for the standalone test plugin.
 *
 * <p>Logs the invoking player's client locale (as the server sees it via {@code Player#getLocale()}) in a
 * deterministic single-line format. Integration tests use this to assert that the locale a {@code FULL_LOGIN}
 * bot sent during the configuration phase actually reached the server, by matching the line in the captured
 * console output.
 */
final class LkTestLocaleCommandExecutor implements CommandExecutor
{
    /**
     * Marker prefix of the logged locale line so tests can identify it in the console output.
     */
    static final String LOCALE_MESSAGE_PREFIX = "LK_LOCALE";
    /**
     * Reply sent when the command is not invoked by a player (only players have a client locale).
     */
    static final String PLAYERS_ONLY_MESSAGE = "Only players can use /lktestlocale.";

    /**
     * Logger the locale line is emitted through.
     */
    private final Logger logger;

    /**
     * @param logger
     *     Logger to emit the locale line through (the owning plugin's logger).
     */
    LkTestLocaleCommandExecutor(Logger logger)
    {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Logs the sender's client locale, or replies with a hint for non-player senders.
     *
     * @param sender
     *     Sender that invoked the command.
     * @param command
     *     Command metadata.
     * @param label
     *     Used command label.
     * @param args
     *     Command arguments (ignored).
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

        logger.info("%s name=%s locale=%s".formatted(LOCALE_MESSAGE_PREFIX, player.getName(), player.getLocale()));
        return true;
    }
}
