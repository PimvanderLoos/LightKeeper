package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentConfigurationTest
{
    @Test
    void fromSystemProperties_shouldReadRequiredValues()
    {
        // setup
        withProperties(Map.of(
            RuntimeProtocol.PROPERTY_SOCKET_PATH, "agent.sock",
            RuntimeProtocol.PROPERTY_AUTH_TOKEN, "token",
            RuntimeProtocol.PROPERTY_PROTOCOL_VERSION, "v1",
            RuntimeProtocol.PROPERTY_EXPECTED_AGENT_SHA256, "abc"
        ), () ->
        {
            // execute
            final AgentConfiguration configuration = AgentConfiguration.fromSystemProperties();

            // verify
            assertThat(configuration.socketPath()).isEqualTo(Path.of("agent.sock").toAbsolutePath());
            assertThat(configuration.authToken()).isEqualTo("token");
            assertThat(configuration.protocolVersion()).isEqualTo("v1");
            assertThat(configuration.expectedAgentSha256()).isEqualTo("abc");
        });
    }

    @Test
    void fromSystemProperties_shouldThrowExceptionWhenRequiredPropertyMissing()
    {
        // setup
        withProperties(Map.of(
            RuntimeProtocol.PROPERTY_SOCKET_PATH, "agent.sock",
            RuntimeProtocol.PROPERTY_PROTOCOL_VERSION, "v1"
        ), () ->
        {
            // execute + verify
            assertThatThrownBy(AgentConfiguration::fromSystemProperties)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RuntimeProtocol.PROPERTY_AUTH_TOKEN);
        });
    }

    @Test
    void constructor_shouldRejectNullArguments()
        throws Exception
    {
        // setup
        final Constructor<AgentConfiguration> constructor = AgentConfiguration.class.getDeclaredConstructor(
            Path.class,
            String.class,
            String.class,
            String.class
        );

        // execute + verify
        assertThatThrownBy(() -> constructor.newInstance(null, "token", "v1", ""))
            .isInstanceOf(InvocationTargetException.class)
            .cause()
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("socketPath");
    }

    private static void withProperties(Map<String, String> properties, Runnable runnable)
    {
        final Map<String, String> previousValues = new java.util.HashMap<>();
        for (final String key : properties.keySet())
            previousValues.put(key, System.getProperty(key));

        try
        {
            properties.forEach(System::setProperty);
            runnable.run();
        }
        finally
        {
            for (final String key : properties.keySet())
            {
                final String previousValue = previousValues.get(key);
                if (previousValue == null)
                    System.clearProperty(key);
                else
                    System.setProperty(key, previousValue);
            }
        }
    }
}
