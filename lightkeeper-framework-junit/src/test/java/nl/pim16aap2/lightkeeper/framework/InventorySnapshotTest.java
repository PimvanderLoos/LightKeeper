package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventorySnapshotTest
{
    @Test
    void constructor_shouldDefensivelyCopyItems()
    {
        // setup
        final List<MenuItemSnapshot> items = new ArrayList<>();
        items.add(new MenuItemSnapshot(0, "minecraft:stone", "Stone", List.of()));

        // execute
        final InventorySnapshot snapshot = new InventorySnapshot(items);
        items.clear();

        // verify
        assertThat(snapshot.items()).hasSize(1);
    }

    @Test
    void findItem_shouldReturnMatchingItemIgnoringCase()
    {
        // setup
        final MenuItemSnapshot item = new MenuItemSnapshot(0, "minecraft:stone", "Stone", List.of());
        final InventorySnapshot snapshot = new InventorySnapshot(List.of(item));

        // execute
        final MenuItemSnapshot result = snapshot.findItem("MINECRAFT:STONE");

        // verify
        assertThat(result).isSameAs(item);
    }

    @Test
    void fromItemMaps_shouldThrowExceptionWhenSlotIsMissing()
    {
        // setup
        final List<Map<String, Object>> items = List.of(Map.of("materialKey", "minecraft:stone"));

        // execute + verify
        assertThatThrownBy(() -> InventorySnapshot.fromItemMaps(items))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("slot");
    }

    @Test
    @SuppressWarnings("NullAway")
    void findItem_shouldThrowExceptionWhenMaterialKeyIsNull()
    {
        // setup
        final InventorySnapshot snapshot = new InventorySnapshot(List.of());

        // execute + verify
        assertThatThrownBy(() -> snapshot.findItem(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("materialKey");
    }
}
