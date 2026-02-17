package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMainThreadExecutorTest
{
    @Test
    void constructor_shouldThrowExceptionWhenPluginIsNull()
        throws Exception
    {
        // setup
        final Constructor<AgentMainThreadExecutor> constructor = AgentMainThreadExecutor.class.getDeclaredConstructor(
            JavaPlugin.class,
            long.class
        );

        // execute + verify
        assertThatThrownBy(() -> constructor.newInstance(null, 1L))
            .isInstanceOf(InvocationTargetException.class)
            .cause()
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("plugin");
    }

    @Test
    void constructor_shouldThrowExceptionWhenTimeoutPropertyIsNotNumeric()
    {
        // setup
        final JavaPlugin plugin = mock();

        // execute
        assertThatThrownBy(() ->
            withSystemProperty(
                RuntimeProtocol.PROPERTY_SYNC_OPERATION_TIMEOUT_SECONDS,
                "not-a-number",
                () -> new AgentMainThreadExecutor(plugin)
            )
        )
            // verify
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(RuntimeProtocol.PROPERTY_SYNC_OPERATION_TIMEOUT_SECONDS)
            .hasRootCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void constructor_shouldThrowExceptionWhenTimeoutPropertyIsNotPositive()
    {
        // setup
        final JavaPlugin plugin = mock();

        // execute
        assertThatThrownBy(() ->
            withSystemProperty(
                RuntimeProtocol.PROPERTY_SYNC_OPERATION_TIMEOUT_SECONDS,
                "0",
                () -> new AgentMainThreadExecutor(plugin)
            )
        )
            // verify
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be > 0");
    }

    @Test
    void constructor_shouldAcceptConfiguredPositiveTimeout()
    {
        // setup
        final JavaPlugin plugin = mock();

        // execute
        assertThatCode(() ->
            withSystemProperty(
                RuntimeProtocol.PROPERTY_SYNC_OPERATION_TIMEOUT_SECONDS,
                "180",
                () -> new AgentMainThreadExecutor(plugin)
            )
        )
            // verify
            .doesNotThrowAnyException();
    }

    @Test
    void constructor_shouldThrowExceptionWhenExplicitTimeoutIsNotPositive()
    {
        // setup
        final JavaPlugin plugin = mock(JavaPlugin.class);

        // execute + verify
        assertThatThrownBy(() -> new AgentMainThreadExecutor(plugin, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be > 0");
    }

    @Test
    void callOnMainThread_shouldExecuteDirectlyWhenAlreadyOnPrimaryThread()
        throws Exception
    {
        // setup
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final AgentMainThreadExecutor executor = new AgentMainThreadExecutor(plugin, 5L);

        try (var mockedBukkit = mockStatic(Bukkit.class))
        {
            mockedBukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            // execute + verify
            assertThatCode(() -> executor.callOnMainThread(() -> "ok"))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void callOnMainThread_shouldUseSchedulerWhenNotOnPrimaryThread()
        throws Exception
    {
        // setup
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final AgentMainThreadExecutor executor = new AgentMainThreadExecutor(plugin, 5L);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final java.util.concurrent.Future<Object> futureTask = CompletableFuture.completedFuture("scheduled");

        try (var mockedBukkit = mockStatic(Bukkit.class))
        {
            mockedBukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            mockedBukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.callSyncMethod(eq(plugin), any())).thenReturn(futureTask);

            // execute
            final String result = executor.callOnMainThread(() -> "ignored");

            // verify
            org.assertj.core.api.Assertions.assertThat(result).isEqualTo("scheduled");
        }
    }

    @Test
    void callOnMainThread_shouldPropagateCallableExceptionWhenAlreadyOnPrimaryThread()
    {
        // setup
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final AgentMainThreadExecutor executor = new AgentMainThreadExecutor(plugin, 5L);

        try (var mockedBukkit = mockStatic(Bukkit.class))
        {
            mockedBukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            // execute + verify
            assertThatThrownBy(() -> executor.callOnMainThread(() ->
            {
                throw new IllegalStateException("boom");
            }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");
        }
    }

    @Test
    void callOnMainThread_shouldPropagateTimeoutExceptionWhenScheduledCallTimesOut()
    {
        // setup
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final AgentMainThreadExecutor executor = new AgentMainThreadExecutor(plugin, 1L);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final FutureTask<Object> neverCompletingFuture = new FutureTask<>(() -> "never");

        try (var mockedBukkit = mockStatic(Bukkit.class))
        {
            mockedBukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            mockedBukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.callSyncMethod(eq(plugin), any())).thenReturn(neverCompletingFuture);

            // execute + verify
            assertThatThrownBy(() -> executor.callOnMainThread(() -> "ignored"))
                .isInstanceOf(TimeoutException.class);
        }
    }

    private static void withSystemProperty(String key, String value, Runnable runnable)
    {
        final String previousValue = System.getProperty(key);
        try
        {
            System.setProperty(key, value);
            runnable.run();
        }
        finally
        {
            if (previousValue == null)
                System.clearProperty(key);
            else
                System.setProperty(key, previousValue);
        }
    }
}
