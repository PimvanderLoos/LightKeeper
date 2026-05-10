package nl.pim16aap2.lightkeeper.framework;

import java.util.List;

/**
 * Snapshot of a player's inventory.
 *
 * @param items
 *     List of non-air item snapshots in the inventory.
 */
public record InventorySnapshot(
    List<MenuItemSnapshot> items
)
{
    /**
     * Finds an item in the inventory by material key.
     *
     * @param materialKey
     *     The material key to look for (e.g. "minecraft:stone").
     * @return The item snapshot, or null if not found.
     */
    public MenuItemSnapshot findItem(String materialKey)
    {
        return items.stream()
            .filter(item -> item.materialKey().equalsIgnoreCase(materialKey))
            .findFirst()
            .orElse(null);
    }
}
