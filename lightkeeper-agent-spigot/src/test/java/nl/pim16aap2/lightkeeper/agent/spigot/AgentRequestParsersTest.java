package nl.pim16aap2.lightkeeper.agent.spigot;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class AgentRequestParsersTest
{
    @Test
    void parseMaterial_shouldReturnNullWhenInputIsBlank()
    {
        // setup
        final String input = "  ";

        // execute
        final var material = AgentRequestParsers.parseMaterial(input);

        // verify
        assertThat(material).isNull();
    }

    @Test
    void parseMaterial_shouldUseDirectMatchWhenAvailable()
    {
        // setup
        try (MockedStatic<org.bukkit.Material> mockedStatic = mockStatic(org.bukkit.Material.class))
        {
            mockedStatic.when(() -> org.bukkit.Material.matchMaterial(eq("stone"), eq(true)))
                .thenReturn(org.bukkit.Material.STONE);

            // execute
            final var material = AgentRequestParsers.parseMaterial("stone");

            // verify
            assertThat(material).isEqualTo(org.bukkit.Material.STONE);
        }
    }

    @Test
    void parseMaterial_shouldFallbackToNormalizedMinecraftPrefix()
    {
        // setup
        try (MockedStatic<org.bukkit.Material> mockedStatic = mockStatic(org.bukkit.Material.class))
        {
            mockedStatic.when(() -> org.bukkit.Material.matchMaterial(eq("minecraft:stone"), eq(true)))
                .thenReturn(null);
            mockedStatic.when(() -> org.bukkit.Material.matchMaterial(eq("STONE"), eq(true)))
                .thenReturn(org.bukkit.Material.STONE);

            // execute
            final var material = AgentRequestParsers.parseMaterial("minecraft:stone");

            // verify
            assertThat(material).isEqualTo(org.bukkit.Material.STONE);
        }
    }

    @Test
    void parseBlockFace_shouldReturnNullWhenInputIsBlank()
    {
        // setup
        final String input = "   ";

        // execute
        final var face = AgentRequestParsers.parseBlockFace(input);

        // verify
        assertThat(face).isNull();
    }

    @Test
    void parseBlockFace_shouldReturnNullWhenInputIsUnknown()
    {
        // setup
        final String input = "not-a-face";

        // execute
        final var face = AgentRequestParsers.parseBlockFace(input);

        // verify
        assertThat(face).isNull();
    }

    @Test
    void parseBlockFace_shouldParseCaseInsensitiveFaceName()
    {
        // setup
        final String input = "north";

        // execute
        final var face = AgentRequestParsers.parseBlockFace(input);

        // verify
        assertThat(face).isEqualTo(org.bukkit.block.BlockFace.NORTH);
    }
}
