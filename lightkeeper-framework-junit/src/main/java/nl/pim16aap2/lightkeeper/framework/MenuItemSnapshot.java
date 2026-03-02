package nl.pim16aap2.lightkeeper.framework;

import java.util.List;

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
}
