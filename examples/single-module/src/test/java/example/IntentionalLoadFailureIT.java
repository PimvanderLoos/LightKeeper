package example;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

/** A valid descriptor may intentionally name an unavailable main class; preparation must still allow the test. */
@EnabledIfSystemProperty(named = "consumer.expectAbsent", matches = "true")
@ExtendWith(LightkeeperExtension.class)
class IntentionalLoadFailureIT
{
    @Test
    void targetPlugin_shouldRemainAbsentWithoutBecomingAnInfrastructureFailure(ILightkeeperFramework framework)
    {
        // setup
        final String pluginName = "ConsumerPlugin";

        // execute
        final var server = assertThat(framework).server();

        // verify
        server.plugin(pluginName).isAbsent();
    }
}
