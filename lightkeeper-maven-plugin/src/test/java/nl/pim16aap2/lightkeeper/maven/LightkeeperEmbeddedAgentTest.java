package nl.pim16aap2.lightkeeper.maven;

import nl.pim16aap2.lightkeeper.provisioning.LightkeeperEmbeddedAgent;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that the Maven adapter can access the shared module's embedded runtime resource. */
class LightkeeperEmbeddedAgentTest
{
    @Test
    void openStream_shouldReturnStreamWhenEmbeddedAgentResourceExists()
        throws Exception
    {
        // setup
        byte[] embeddedAgentContents;

        // execute
        try (InputStream embeddedAgentStream = LightkeeperEmbeddedAgent.openStream())
        {
            embeddedAgentContents = embeddedAgentStream.readAllBytes();
        }

        // verify
        assertThat(embeddedAgentContents).isNotEmpty();
    }
}
