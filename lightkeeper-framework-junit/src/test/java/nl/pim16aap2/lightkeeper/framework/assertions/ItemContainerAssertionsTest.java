package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IFrameworkGatewayView;
import nl.pim16aap2.lightkeeper.framework.InventorySnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Tests shared item semantics, frozen navigation and live player observations. */
@ExtendWith(MockitoExtension.class)
class ItemContainerAssertionsTest
{
    /** Read-only player gateway. */
    @Mock
    private IFrameworkGatewayView gateway;

    /** Expected Bukkit item whose represented fields are compared. */
    @Mock
    private ItemStack expectedItem;

    /** Metadata carried by the expected item. */
    @Mock
    private ItemMeta expectedMeta;

    /** Verifies that tests leave no unaccounted mock interactions. */
    @AfterEach
    void verifyInteractions()
    {
        verifyNoMoreInteractions(gateway, expectedItem, expectedMeta);
    }

    @Test
    void itemAt_shouldExposeNormalizedMaterialNameAndOrderedLore()
    {
        // setup
        final var inventory = new InventorySnapshot(List.of(item()));

        // execute
        final var selected = assertThat(inventory)
            .hasOccupiedSlotCount(1)
            .hasEmptySlot(2)
            .itemAt(1);

        // verify
        selected
            .isMaterial("STONE")
            .hasDisplayName("Button")
            .lore()
            .containsExactly("First", "Second");
        assertThat(inventory)
            .hasItemAt(1, "stone")
            .items()
            .containsExactly(item());
    }

    @Test
    void itemAt_shouldReportMissingSlotAndObservedItems()
    {
        // setup
        final var inventory = new InventorySnapshot(List.of(item()));

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(inventory).itemAt(3));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("slot 3", "minecraft:stone");
    }

    @Test
    void hasItemAt_shouldReportWrongMaterial()
    {
        // setup
        final var inventory = new InventorySnapshot(List.of(item()));

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(inventory).hasItemAt(1, "dirt"));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("slot 1", "minecraft:dirt", "minecraft:stone");
    }

    @Test
    void hasEmptySlot_shouldRejectOccupiedSlot()
    {
        // setup
        final var inventory = new InventorySnapshot(List.of(item()));

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(inventory).hasEmptySlot(1));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("slot 1", "minecraft:stone");
    }

    @Test
    void slotAssertions_shouldRejectNegativeIndices()
    {
        // setup
        final var assertion = assertThat(new InventorySnapshot(List.of()));

        // execute
        final Throwable emptyFailure = catchThrowable(() -> assertion.hasEmptySlot(-1));
        final Throwable itemFailure = catchThrowable(() -> assertion.itemAt(-1));
        final Throwable materialFailure = catchThrowable(() -> assertion.hasItemAt(-1, "stone"));

        // verify
        assertThat(emptyFailure).isInstanceOf(IllegalArgumentException.class);
        assertThat(itemFailure).isInstanceOf(IllegalArgumentException.class);
        assertThat(materialFailure).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasOccupiedSlotCount_shouldRejectNegativeCount()
    {
        // setup
        final var assertion = assertThat(new InventorySnapshot(List.of()));

        // execute
        final Throwable failure = catchThrowable(() -> assertion.hasOccupiedSlotCount(-1));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasItemAt_shouldCompareAllRepresentedBukkitFields()
    {
        // setup
        when(expectedItem.getType()).thenReturn(Material.STONE);
        when(expectedItem.getItemMeta()).thenReturn(expectedMeta);
        when(expectedMeta.getDisplayName()).thenReturn("Button");
        when(expectedMeta.getLore()).thenReturn(List.of("First", "Second"));
        final var assertion = assertThat(new InventorySnapshot(List.of(item())));

        // execute
        assertion.hasItemAt(1, expectedItem);

        // verify
        verify(expectedItem).getType();
        verify(expectedItem).getItemMeta();
    }

    @Test
    void hasItemAt_shouldRejectDifferentLoreEvenWhenMaterialAndNameMatch()
    {
        // setup
        when(expectedItem.getType()).thenReturn(Material.STONE);
        when(expectedItem.getItemMeta()).thenReturn(expectedMeta);
        when(expectedMeta.getDisplayName()).thenReturn("Button");
        when(expectedMeta.getLore()).thenReturn(List.of("Different"));

        // execute
        final Throwable failure = catchThrowable(
            () -> assertThat(new InventorySnapshot(List.of(item()))).hasItemAt(1, expectedItem)
        );

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("slot 1", "lore");
    }

    @Test
    void inventory_shouldCaptureFrozenValuesAndReadAgainOnNewNavigation()
    {
        // setup
        final UUID id = UUID.randomUUID();
        final var player = FrameworkHandleFactory.playerHandle(gateway, id, "tester");
        when(gateway.playerInventory(id)).thenReturn(
            new InventorySnapshot(List.of(item())),
            new InventorySnapshot(List.of())
        );
        final var playerAssertion = assertThat(player);

        // execute
        final var earlier = playerAssertion.inventory();
        final var later = playerAssertion.inventory();

        // verify
        earlier.hasItemAt(1, "STONE");
        later.isEmpty();
        verify(gateway, times(2)).playerInventory(id);
    }

    @Test
    void receivedMessages_shouldKeepMessageBoundariesAndObserveNewCaptures()
    {
        // setup
        final UUID id = UUID.randomUUID();
        final var player = FrameworkHandleFactory.playerHandle(gateway, id, "tester");
        when(gateway.playerMessages(id)).thenReturn(List.of("hel", "lo"), List.of("hello"));
        final var playerAssertion = assertThat(player);

        // execute
        final var earlier = playerAssertion.receivedMessages();
        final var later = playerAssertion.receivedMessages();

        // verify
        earlier
            .containsExactly("hel", "lo")
            .doesNotContain("hello");
        later.containsExactly("hello");
        verify(gateway, times(2)).playerMessages(id);
    }

    @Test
    void snapshots_shouldCopyItemsAndLore()
    {
        // setup
        final var lore = new ArrayList<>(List.of("First"));
        final var observed = new MenuItemSnapshot(1, "stone", " ", lore);
        final var items = new ArrayList<>(List.of(observed));
        final var inventory = new InventorySnapshot(items);

        // execute
        lore.clear();
        items.clear();

        // verify
        assertThat(inventory)
            .hasOccupiedSlotCount(1)
            .itemAt(1)
            .hasDisplayName("")
            .lore()
            .containsExactly("First");
    }

    @Test
    void isEmpty_shouldRejectNullInventory()
    {
        // setup
        final var assertion = new InventoryAssert(null);

        // execute
        final Throwable failure = catchThrowable(assertion::isEmpty);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
    }

    private static MenuItemSnapshot item()
    {
        return new MenuItemSnapshot(1, "stone", "Button", List.of("First", "Second"));
    }
}
