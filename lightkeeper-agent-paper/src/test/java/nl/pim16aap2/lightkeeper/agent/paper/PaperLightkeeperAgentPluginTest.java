package nl.pim16aap2.lightkeeper.agent.paper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaperLightkeeperAgentPluginTest
{
    @Test
    void extractCraftBukkitRevision_shouldReturnRevisionForValidPackage()
    {
        // setup
        final String packageName = "org.bukkit.craftbukkit.v1_21_R6";

        // execute
        final String revision = PaperLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName);

        // verify
        assertThat(revision).isEqualTo("v1_21_R6");
    }

    @Test
    void extractCraftBukkitRevision_shouldReturnRevisionForR7Package()
    {
        // setup
        final String packageName = "org.bukkit.craftbukkit.v1_21_R7";

        // execute
        final String revision = PaperLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName);

        // verify
        assertThat(revision).isEqualTo("v1_21_R7");
    }

    @Test
    void extractCraftBukkitRevision_shouldReturnNullForUnversionedCraftBukkitPackage()
    {
        // setup
        final String packageName = "org.bukkit.craftbukkit";

        // execute
        final String revision = PaperLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName);

        // verify
        assertThat(revision).isNull();
    }

    @Test
    void extractCraftBukkitRevision_shouldThrowExceptionForUnexpectedPackagePrefix()
    {
        // setup
        final String packageName = "org.bukkit.server.v1_21_R6";

        // execute + verify
        assertThatThrownBy(() -> PaperLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unexpected CraftBukkit package");
    }

    @Test
    void extractCraftBukkitRevision_shouldThrowExceptionWhenRevisionCannotBeResolved()
    {
        // setup
        final String packageName = "org.bukkit.craftbukkit.";

        // execute + verify
        assertThatThrownBy(() -> PaperLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to resolve CraftBukkit NMS revision");
    }
}
