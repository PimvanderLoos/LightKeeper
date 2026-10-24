package nl.pim16aap2.lightkeeper.framework;

import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Menu item snapshot.
 *
 * @param slot
 *     Raw slot index.
 * @param materialKey
 *     Material key.
 * @param displayName
 *     Optional display name.
 * @param lore
 *     Lore lines.
 */
public record MenuItemSnapshot(
    int slot,
    String materialKey,
    String displayName,
    List<String> lore
)
{
    /** Copies lore and normalizes material keys and absent display names. */
    public MenuItemSnapshot
    {
        materialKey = MaterialKeys.normalize(materialKey);
        displayName = normalizedName(displayName);
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    /**
     * Compares normalized material keys.
     *
     * @param expected
     *     Material name or namespaced key.
     * @return Whether the materials match.
     */
    public boolean matchesMaterial(@Nullable String expected)
    {
        if (expected == null)
        {
            return false;
        }
        return materialKey.equals(MaterialKeys.normalize(expected));
    }

    /**
     * Compares only material, display name and ordered lore, excluding unobserved metadata and amount.
     *
     * @param expected
     *     Expected Bukkit item.
     * @return Whether all represented fields match.
     */
    public boolean matches(ItemStack expected)
    {
        Objects.requireNonNull(expected, "expected may not be null.");
        if (!matchesMaterial(typeKeyAsString(expected)))
        {
            return false;
        }

        final var meta = expected.getItemMeta();
        final String expectedName = meta == null ? "" : normalizedName(meta.getDisplayName());
        final List<String> expectedLore = meta == null ? null : meta.getLore();

        return displayName.equals(expectedName) &&
            lore.equals(expectedLore == null ? List.of() : expectedLore);
    }

    private static @Nullable String typeKeyAsString(@Nullable ItemStack item)
    {
        if (item == null)
        {
            return null;
        }

        final var key = item.getType().getKeyOrNull();
        return key == null ? null : key.toString();
    }

    private static String normalizedName(@Nullable String name)
    {
        return name == null || name.isBlank() ? "" : name;
    }
}
