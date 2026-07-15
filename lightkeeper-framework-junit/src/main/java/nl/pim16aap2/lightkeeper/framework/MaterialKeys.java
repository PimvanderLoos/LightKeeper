package nl.pim16aap2.lightkeeper.framework;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalization for material keys across the framework API.
 */
public final class MaterialKeys
{
    private MaterialKeys()
    {
    }

    /**
     * Normalizes a material key to its canonical namespaced, lower-case form.
     *
     * @param materialKey
     *     Material key, e.g. {@code STONE}, {@code stone}, or {@code minecraft:stone}.
     * @return The canonical form, e.g. {@code minecraft:stone}.
     */
    public static String normalize(String materialKey)
    {
        final String trimmed =
            Objects.requireNonNull(materialKey, "materialKey may not be null.").trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty())
            throw new IllegalArgumentException("materialKey may not be blank.");
        return trimmed.startsWith("minecraft:") ? trimmed : "minecraft:" + trimmed;
    }
}
