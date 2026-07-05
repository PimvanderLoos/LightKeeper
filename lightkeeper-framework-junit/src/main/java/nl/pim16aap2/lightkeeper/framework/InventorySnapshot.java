package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.ItemSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;
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
     * Creates an {@link InventorySnapshot} from the typed item snapshots returned by the agent RPC.
     *
     * @param protocolItems
     *     Typed item snapshots from {@code UdsAgentClient#getPlayerInventory}.
     * @return
     *     Inventory snapshot.
     */
    public static InventorySnapshot fromItems(List<ItemSnapshot> protocolItems)
    {
        Objects.requireNonNull(protocolItems, "items may not be null.");
        final List<MenuItemSnapshot> items = protocolItems.stream()
            .map(item -> new MenuItemSnapshot(
                item.slot(),
                item.materialKey(),
                Objects.requireNonNullElse(item.displayName(), ""),
                item.lore()))
            .toList();
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
