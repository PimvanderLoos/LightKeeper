package nl.pim16aap2.lightkeeper.spigot.testplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Handles {@code /lktestgui} command execution for the standalone test plugin.
 */
final class LkTestGuiCommandExecutor implements CommandExecutor
{
    static final String COMMAND_PERMISSION = "lightkeeper.testplugin.lktestgui";
    static final String NON_PLAYER_MESSAGE = "Only players can use this command.";
    static final String NO_PERMISSION_MESSAGE = "You do not have permission to use /lktestgui.";

    /**
     * Menu service that owns GUI construction and display.
     */
    private final GuiMenuService guiMenuService;

    /**
     * @param guiMenuService
     *     Menu service used to open the GUI flow.
     */
    LkTestGuiCommandExecutor(GuiMenuService guiMenuService)
    {
        this.guiMenuService = Objects.requireNonNull(guiMenuService, "guiMenuService");
    }

    /**
     * Validates sender permissions and opens the main GUI for authorized players.
     *
     * @param sender
     *     Sender that invoked the command.
     * @param command
     *     Command metadata.
     * @param label
     *     Used command label.
     * @param args
     *     Command arguments.
     * @return
     *     Always {@code true} because this executor handles all command outcomes directly.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player player))
        {
            sender.sendMessage(NON_PLAYER_MESSAGE);
            return true;
        }

        if (!player.hasPermission(COMMAND_PERMISSION))
        {
            player.sendMessage(NO_PERMISSION_MESSAGE);
            return true;
        }

        guiMenuService.openMainMenu(player);
        return true;
    }
}
