package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Shared parser utilities for converting string request arguments to typed values.
 *
 * <p>This class centralizes argument normalization rules so protocol handlers apply consistent parsing behavior.
 */
final class AgentRequestParsers
{
    /**
     * Utility class.
     */
    private AgentRequestParsers()
    {
    }

    /**
     * Parses a trimmed integer value.
     *
     * @param value
     *     Input string.
     * @return
     *     Parsed integer.
     */
    static int parseInt(String value)
    {
        return Integer.parseInt(value.trim());
    }

    /**
     * Parses a trimmed long value.
     *
     * @param value
     *     Input string.
     * @return
     *     Parsed long.
     */
    static long parseLong(String value)
    {
        return Long.parseLong(value.trim());
    }

    /**
     * Parses a nullable/blankable decimal argument.
     *
     * @param value
     *     Nullable input string.
     * @return
     *     Parsed decimal value, or {@code null} when input is absent/blank.
     */
    static @Nullable Double parseOptionalDouble(@Nullable String value)
    {
        if (value == null || value.isBlank())
            return null;
        return Double.parseDouble(value.trim());
    }

    /**
     * Resolves a material from namespaced or plain material identifiers.
     *
     * @param materialName
     *     Nullable material name, for example {@code stone} or {@code minecraft:stone}.
     * @return
     *     Matching material, or {@code null} when no match exists.
     */
    static @Nullable Material parseMaterial(@Nullable String materialName)
    {
        final String trimmed = materialName == null ? "" : materialName.trim();
        if (trimmed.isEmpty())
            return null;

        final Material directMatch = Material.matchMaterial(trimmed, true);
        if (directMatch != null)
            return directMatch;

        final String normalized = trimmed.startsWith("minecraft:")
            ? trimmed.substring("minecraft:".length())
            : trimmed;
        return Material.matchMaterial(normalized.toUpperCase(Locale.ROOT), true);
    }

    /**
     * Parses a block face name.
     *
     * @param blockFaceName
     *     Nullable block face name.
     * @return
     *     Parsed block face, or {@code null} when the input is absent or unknown.
     */
    static @Nullable BlockFace parseBlockFace(@Nullable String blockFaceName)
    {
        final String trimmed = blockFaceName == null ? "" : blockFaceName.trim();
        if (trimmed.isEmpty())
            return null;

        try
        {
            return BlockFace.valueOf(trimmed.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception)
        {
            return null;
        }
    }
}
