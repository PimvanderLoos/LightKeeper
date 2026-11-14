package nl.pim16aap2.lightkeeper.provisioning;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static org.assertj.core.api.Assertions.*;

class LightkeeperEmbeddedAgentTest
{
    @Test
    void openStream_shouldExposeAgentJarFromSharedProvisioningClasspath()
        throws Exception
    {
        // setup
        final List<String> entries = new ArrayList<>();

        // execute
        try (var jar = new JarInputStream(LightkeeperEmbeddedAgent.openStream()))
        {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null)
            {
                entries.add(entry.getName());
            }
        }

        // verify
        assertThat(entries).contains(
            "plugin.yml",
            "nl/pim16aap2/lightkeeper/agent/spigot/SpigotLightkeeperAgentPlugin.class"
        );
    }
}
