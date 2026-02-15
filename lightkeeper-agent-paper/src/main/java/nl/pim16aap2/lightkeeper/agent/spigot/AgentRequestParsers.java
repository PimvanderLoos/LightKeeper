package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

final class AgentRequestParsers
{
    private AgentRequestParsers()
    {
    }

    static int parseInt(String value)
    {
        return Integer.parseInt(value.trim());
    }

    static long parseLong(String value)
    {
        return Long.parseLong(value.trim());
    }

    static @Nullable Double parseOptionalDouble(@Nullable String value)
    {
        if (value == null || value.isBlank())
            return null;
        return Double.parseDouble(value.trim());
    }

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
}
