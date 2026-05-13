package nl.pim16aap2.lightkeeper.framework;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * Creates a snapshot with a defensive immutable copy of the item list.
     */
    public InventorySnapshot
    {
        items = List.copyOf(Objects.requireNonNull(items, "items may not be null."));
    }

    /**
     * Creates an {@link InventorySnapshot} from the raw item-map list returned by the agent RPC.
     *
     * <p>Each map must contain {@code slot} (Number), {@code materialKey} (String), optionally
     * {@code displayName} (String), and optionally {@code lore} (List&lt;String&gt;).
     *
     * @param itemMaps
     *     Raw item maps from {@code UdsAgentClient#getPlayerInventory}.
     * @return
     *     Inventory snapshot.
     */
    @SuppressWarnings("unchecked")
    public static InventorySnapshot fromItemMaps(List<Map<String, Object>> itemMaps)
    {
        Objects.requireNonNull(itemMaps, "itemMaps may not be null.");
        final List<MenuItemSnapshot> items = itemMaps.stream().map(m ->
        {
            final int slot = ((Number) m.get("slot")).intValue();
            final String materialKey = (String) m.getOrDefault("materialKey", "");
            final String displayName = (String) m.getOrDefault("displayName", "");
            final List<String> lore = (List<String>) m.getOrDefault("lore", List.of());
            return new MenuItemSnapshot(slot, materialKey, displayName == null ? "" : displayName, lore);
        }).toList();
        return new InventorySnapshot(items);
    }

    /**
     * Finds an item in the inventory by material key.
     *
     * @param materialKey
     *     The material key to look for (e.g. "minecraft:stone").
     * @return The item snapshot, or null if not found.
     */
    public @Nullable MenuItemSnapshot findItem(String materialKey)
    {
        Objects.requireNonNull(materialKey, "materialKey may not be null.");
        return items.stream()
            .filter(item -> item.materialKey().equalsIgnoreCase(materialKey))
            .findFirst()
            .orElse(null);
    }
}
