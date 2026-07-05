package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Shared parser utilities for converting typed command arguments to Bukkit domain objects.
 *
 * <p>This class centralizes material and block-face resolution so protocol handlers apply consistent
 * parsing behavior.
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
