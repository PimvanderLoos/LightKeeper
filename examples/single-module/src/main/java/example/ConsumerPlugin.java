package example;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Tiny production plugin whose command exposes packaged source and filtered resource changes. */
public final class ConsumerPlugin extends JavaPlugin
{
    /** Returns the revision from the deployed JAR, including whether a removed resource is still present. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments)
    {
        try (var revision = getResource("revision.txt"); var removed = getResource("removed.txt"))
        {
            if (revision == null)
            {
                throw new IllegalStateException("Missing packaged revision.txt");
            }
            sender.sendMessage("code-v1:" + new String(revision.readAllBytes(), StandardCharsets.UTF_8).trim()
                + ":" + (removed == null ? "removed" : "present"));
            return true;
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Cannot read packaged revision", exception);
        }
    }
}
