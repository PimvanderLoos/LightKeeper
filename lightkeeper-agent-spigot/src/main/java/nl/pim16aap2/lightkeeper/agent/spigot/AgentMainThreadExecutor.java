package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
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
     * Default maximum time to wait for a scheduled synchronous server operation.
     */
    private static final long DEFAULT_SYNC_OPERATION_TIMEOUT_SECONDS = 120L;

    /**
     * Plugin context required by Bukkit's scheduler APIs.
     */
    private final JavaPlugin plugin;
    /**
     * Maximum time to wait for a scheduled synchronous server operation.
     */
    private final long syncOperationTimeoutSeconds;

    /**
     * @param plugin
     *     Owning plugin used when scheduling main-thread tasks.
     */
    AgentMainThreadExecutor(JavaPlugin plugin)
    {
        this(plugin, readSyncOperationTimeoutSeconds());
    }

    /**
     * @param plugin
     *     Owning plugin used when scheduling main-thread tasks.
     * @param syncOperationTimeoutSeconds
     *     Maximum wait duration for scheduled synchronous operations.
     */
    AgentMainThreadExecutor(JavaPlugin plugin, long syncOperationTimeoutSeconds)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        if (syncOperationTimeoutSeconds <= 0L)
        {
            throw new IllegalArgumentException(
                "syncOperationTimeoutSeconds must be > 0 but was " + syncOperationTimeoutSeconds + "."
            );
        }
        this.syncOperationTimeoutSeconds = syncOperationTimeoutSeconds;
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
        if (Bukkit.isPrimaryThread())
            return callable.call();

        return Bukkit.getScheduler()
            .callSyncMethod(plugin, callable)
            .get(syncOperationTimeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Reads and validates the optional sync operation timeout override.
     *
     * @return
     *     Positive timeout value in seconds.
     */
    private static long readSyncOperationTimeoutSeconds()
    {
        final String configuredValue = System.getProperty(
            RuntimeProtocol.PROPERTY_SYNC_OPERATION_TIMEOUT_SECONDS,
            Long.toString(DEFAULT_SYNC_OPERATION_TIMEOUT_SECONDS)
        ).trim();
        try
        {
            final long parsedValue = Long.parseLong(configuredValue);
            if (parsedValue <= 0L)
            {
                throw new IllegalStateException(
                    "System property '%s' must be > 0 but was '%s'."
                        .formatted(RuntimeProtocol.PROPERTY_SYNC_OPERATION_TIMEOUT_SECONDS, configuredValue)
                );
            }
            return parsedValue;
        }
        catch (NumberFormatException exception)
        {
            throw new IllegalStateException(
                "System property '%s' must be a whole number of seconds but was '%s'."
                    .formatted(RuntimeProtocol.PROPERTY_SYNC_OPERATION_TIMEOUT_SECONDS, configuredValue),
                exception
            );
        }
    }
}
