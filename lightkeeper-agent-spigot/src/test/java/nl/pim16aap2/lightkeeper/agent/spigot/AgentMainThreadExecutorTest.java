package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AgentMainThreadExecutorTest
{
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
