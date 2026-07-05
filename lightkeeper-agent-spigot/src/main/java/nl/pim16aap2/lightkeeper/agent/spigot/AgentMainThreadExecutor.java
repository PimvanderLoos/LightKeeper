package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes callables on the Bukkit primary thread with a bounded wait time.
 *
 * <p>All world, player, and inventory mutations should pass through this class to keep thread access consistent.
 */
final class AgentMainThreadExecutor
{
    /**
     * Default maximum time to wait for a scheduled synchronous server operation. Shared with the client so its
     * response timeout stays strictly larger (see {@link RuntimeProtocol#DEFAULT_SYNC_OPERATION_TIMEOUT_SECONDS}).
     */
    private static final long DEFAULT_SYNC_OPERATION_TIMEOUT_SECONDS =
        RuntimeProtocol.DEFAULT_SYNC_OPERATION_TIMEOUT_SECONDS;

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
     *     Propagates the callable's own failure (unwrapped from {@link ExecutionException}); throws
     *     {@link AgentProtocolException} with {@link AgentErrorCode#TIMEOUT} when the operation exceeds the
     *     configured timeout, or {@link AgentErrorCode#INTERRUPTED} when the waiting thread is interrupted.
     */
    <T> T callOnMainThread(Callable<T> callable)
        throws Exception
    {
        if (Bukkit.isPrimaryThread())
            return callable.call();

        final Future<T> future = Bukkit.getScheduler().callSyncMethod(plugin, callable);
        try
        {
            return future.get(syncOperationTimeoutSeconds, TimeUnit.SECONDS);
        }
        catch (ExecutionException exception)
        {
            // Surface the callable's real failure (e.g. an IllegalArgumentException thrown inside a handler
            // lambda) instead of the opaque Future wrapper, so the dispatcher can map it to its domain code.
            final Throwable cause = exception.getCause();
            if (cause instanceof Error error)
                throw error;
            if (cause instanceof Exception causeException)
                throw causeException;
            throw exception;
        }
        catch (TimeoutException exception)
        {
            // Cancel so the still-pending Bukkit task cannot execute later and mutate the next test's state.
            future.cancel(true);
            throw new AgentProtocolException(
                AgentErrorCode.TIMEOUT,
                "Server operation did not complete within %d seconds.".formatted(syncOperationTimeoutSeconds),
                exception
            );
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new AgentProtocolException(
                AgentErrorCode.INTERRUPTED,
                "Interrupted while waiting for a server operation to complete.",
                exception
            );
        }
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
