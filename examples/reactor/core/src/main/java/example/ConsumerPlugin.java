package example;

import example.internal.Revision;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Production plugin packaged first by Shade, then by the separate Assembly module. */
public final class ConsumerPlugin extends JavaPlugin
{
    /** Emits a value from the relocated production class. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments)
    {
        sender.sendMessage(Revision.value());
        return true;
    }
}
