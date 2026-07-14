package nl.pim16aap2.lightkeeper.spigot.testplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles {@code /lktesterror} command execution for the standalone test plugin.
 *
 * <p>Deliberately emits log events at a requested level so integration tests can exercise LightKeeper's
 * structured server-error capture end-to-end: {@code /lktesterror severe} logs a SEVERE message with an
 * attached throwable, {@code /lktesterror warning} logs a plain WARNING.
 */
final class LkTestErrorCommandExecutor implements CommandExecutor
{
    /**
     * Marker embedded in the SEVERE log message so tests can identify the provoked event.
     */
    static final String SEVERE_MESSAGE = "LK_TEST_ERROR provoked severe";
    /**
     * Marker embedded in the WARNING log message so tests can identify the provoked event.
     */
    static final String WARNING_MESSAGE = "LK_TEST_ERROR provoked warning";
    /**
     * Message of the throwable attached to the SEVERE log event.
     */
    static final String THROWABLE_MESSAGE = "LK_TEST_ERROR boom";
    /**
     * Reply sent for an unknown or missing mode argument.
     */
    static final String USAGE_MESSAGE = "Usage: /lktesterror <severe|warning>";

    /**
     * Logger the provoked events are emitted through.
     */
    private final Logger logger;

    /**
     * @param logger
     *     Logger to emit the provoked events through (the owning plugin's logger).
     */
    LkTestErrorCommandExecutor(Logger logger)
    {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Emits a log event at the requested level.
     *
     * @param sender
     *     Sender that invoked the command.
     * @param command
     *     Command metadata.
     * @param label
     *     Used command label.
     * @param args
     *     Command arguments; the first argument selects the mode ({@code severe} or {@code warning}).
     * @return
     *     Always {@code true} because this executor handles all command outcomes directly.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        final String mode = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (mode)
        {
            case "severe" -> logger.log(Level.SEVERE, SEVERE_MESSAGE, new IllegalStateException(THROWABLE_MESSAGE));
            case "warning" -> logger.log(Level.WARNING, WARNING_MESSAGE);
            default -> sender.sendMessage(USAGE_MESSAGE);
        }
        return true;
    }
}
