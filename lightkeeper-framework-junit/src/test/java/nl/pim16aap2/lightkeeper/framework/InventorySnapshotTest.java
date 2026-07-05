package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.ItemSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
    void fromItems_shouldMapProtocolItemsAndDefaultNullDisplayName()
    {
        // setup
        final List<ItemSnapshot> protocolItems = List.of(
            new ItemSnapshot(2, "minecraft:stone", null, List.of("lore")));

        // execute
        final InventorySnapshot snapshot = InventorySnapshot.fromItems(protocolItems);

        // verify — a null displayName maps to an empty string
        assertThat(snapshot.items()).singleElement().satisfies(item ->
        {
            assertThat(item.slot()).isEqualTo(2);
            assertThat(item.materialKey()).isEqualTo("minecraft:stone");
            assertThat(item.displayName()).isEmpty();
            assertThat(item.lore()).containsExactly("lore");
        });
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
