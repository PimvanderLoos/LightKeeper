package nl.pim16aap2.lightkeeper.framework.assertions;

import java.util.Locale;

final class MaterialKeyNormalizer
{
    private MaterialKeyNormalizer()
    {
    }

    static String normalizeMaterial(String materialKey)
    {
        final String trimmed = materialKey.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("minecraft:") ? trimmed : "minecraft:" + trimmed;
    }
}
