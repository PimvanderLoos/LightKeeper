package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Executes callables on the Bukkit primary thread with a bounded wait time.
 *
 * <p>All world, player, and inventory mutations should pass through this class to keep thread access consistent.
 */
final class AgentMainThreadExecutor
{
    /**
     * Maximum time to wait for a scheduled synchronous server operation.
     */
    private static final long SYNC_OPERATION_TIMEOUT_SECONDS = 30L;

    /**
     * Plugin context required by Bukkit's scheduler APIs.
     */
    private final JavaPlugin plugin;

    /**
     * @param plugin
     *     Owning plugin used when scheduling main-thread tasks.
     */
    AgentMainThreadExecutor(JavaPlugin plugin)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Submits the callable to the Bukkit main thread and waits for completion.
     *
     * @param callable
     *     Operation to execute synchronously on the server thread.
     * @param <T>
     *     Callable return type.
     * @return
     *     Result returned by the callable.
     * @throws Exception
     *     Propagates execution failures, interruption, and timeout errors.
     */
    <T> T callOnMainThread(Callable<T> callable)
        throws Exception
    {
        return Bukkit.getScheduler()
            .callSyncMethod(plugin, callable)
            .get(SYNC_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
