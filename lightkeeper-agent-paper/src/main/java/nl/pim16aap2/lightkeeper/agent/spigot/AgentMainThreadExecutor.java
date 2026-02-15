package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

final class AgentMainThreadExecutor
{
    private static final long SYNC_OPERATION_TIMEOUT_SECONDS = 30L;

    private final JavaPlugin plugin;

    AgentMainThreadExecutor(JavaPlugin plugin)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    <T> T callOnMainThread(Callable<T> callable)
        throws Exception
    {
        return Bukkit.getScheduler()
            .callSyncMethod(plugin, callable)
            .get(SYNC_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
