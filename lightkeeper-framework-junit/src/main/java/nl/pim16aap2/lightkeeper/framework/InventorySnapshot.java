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
    public static InventorySnapshot fromItemMaps(List<Map<String, Object>> itemMaps)
    {
        Objects.requireNonNull(itemMaps, "itemMaps may not be null.");
        final List<MenuItemSnapshot> items = itemMaps.stream()
            .map(InventorySnapshot::fromItemMap)
            .toList();
        return new InventorySnapshot(items);
    }

    private static MenuItemSnapshot fromItemMap(Map<String, Object> itemMap)
    {
        Objects.requireNonNull(itemMap, "item map may not be null.");

        final Object slotValue = itemMap.get("slot");
        if (!(slotValue instanceof Number slot))
            throw new IllegalArgumentException("Inventory item field 'slot' must be a number.");

        final Object materialKeyValue = itemMap.get("materialKey");
        if (!(materialKeyValue instanceof String materialKey) || materialKey.isBlank())
            throw new IllegalArgumentException("Inventory item field 'materialKey' must be a non-blank string.");

        final Object displayNameValue = itemMap.get("displayName");
        if (displayNameValue != null && !(displayNameValue instanceof String))
            throw new IllegalArgumentException("Inventory item field 'displayName' must be a string or null.");
        final String displayName = Objects.requireNonNullElse((String) displayNameValue, "");

        final Object loreValue = itemMap.get("lore");
        if (loreValue != null && !(loreValue instanceof List<?>))
            throw new IllegalArgumentException("Inventory item field 'lore' must be a list or null.");
        final List<String> lore = loreValue == null
            ? List.of()
            : ((List<?>) loreValue).stream().map(String::valueOf).toList();

        return new MenuItemSnapshot(slot.intValue(), materialKey, displayName, lore);
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
