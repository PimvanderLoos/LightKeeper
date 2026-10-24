package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.MaterialKeys;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Shared slot and item rules for inventories and open menus.
 *
 * @param <S>
 *     Self type: the concrete assertion returned by fluent operations.
 * @param <A>
 *     Actual type: the container subject being asserted.
 */
abstract class AbstractItemContainerAssert<S extends AbstractItemContainerAssert<S, A>, A>
    extends AbstractAssert<S, @Nullable A>
{
    AbstractItemContainerAssert(@Nullable A actual, Class<?> selfType)
    {
        super(actual, selfType);
    }

    /** @return A single observation of the container's non-air items. */
    protected abstract List<MenuItemSnapshot> itemsSnapshot();

    /** @return Frozen item-list assertions; recreate navigation when retrying. */
    public ListAssert<MenuItemSnapshot> items()
    {
        return extracting(ignored -> itemsSnapshot(), Assertions::assertThat);
    }

    /** @return This assertion after checking that no non-air items are present. */
    public S isEmpty()
    {
        items().isEmpty();
        return myself;
    }

    /**
     * Checks occupied slots rather than total item units.
     *
     * @param expected
     *     Expected nonnegative occupied-slot count.
     * @return This assertion.
     */
    public S hasOccupiedSlotCount(int expected)
    {
        if (expected < 0)
            throw new IllegalArgumentException("expected must be >= 0.");
        items().hasSize(expected);
        return myself;
    }

    /**
     * Selects an occupied slot from one snapshot.
     *
     * @param slot
     *     Nonnegative slot index.
     * @return Frozen item assertions.
     */
    public MenuItemSnapshotAssert itemAt(int slot)
    {
        validateSlot(slot);
        return extracting(ignored -> requireItem(slot), MenuItemSnapshotAssert::new);
    }

    /**
     * Checks that no non-air item was observed at a slot.
     *
     * <p>The snapshot does not expose capacity, so an empty slot does not establish upper-bound validity.
     *
     * @param slot
     *     Nonnegative slot index.
     * @return This assertion.
     */
    public S hasEmptySlot(int slot)
    {
        validateSlot(slot);
        final var item = itemsSnapshot().stream()
            .filter(candidate -> candidate.slot() == slot)
            .findFirst();
        if (item.isPresent())
            failWithMessage("Expected slot %d to be empty, but contained %s.", slot, item.get());
        return myself;
    }

    /**
     * Checks a slot's material.
     *
     * @param slot
     *     Nonnegative slot index.
     * @param materialKey
     *     Material name or namespaced key.
     * @return This assertion.
     */
    public S hasItemAt(int slot, String materialKey)
    {
        final String normalized = MaterialKeys.normalize(materialKey);
        return hasMatchingItem(slot, item -> item.matchesMaterial(normalized), "material '" + normalized + "'");
    }

    /**
     * Compares represented item fields, excluding amount and unobserved metadata.
     *
     * @param slot
     *     Nonnegative slot index.
     * @param expected
     *     Expected Bukkit item.
     * @return This assertion.
     */
    public S hasItemAt(int slot, ItemStack expected)
    {
        Objects.requireNonNull(expected, "expected may not be null.");
        return hasMatchingItem(
            slot,
            item -> item.matches(expected),
            "the expected item's material, display name and lore"
        );
    }

    private S hasMatchingItem(int slot, Predicate<MenuItemSnapshot> predicate, String expectation)
    {
        validateSlot(slot);
        final var item = requireItem(slot);
        if (!predicate.test(item))
            failWithMessage("Expected item at slot %d to match %s, but was %s.", slot, expectation, item);
        return myself;
    }

    private MenuItemSnapshot requireItem(int slot)
    {
        final var observed = itemsSnapshot();
        final var item = observed.stream()
            .filter(candidate -> candidate.slot() == slot)
            .findFirst();
        if (item.isEmpty())
            failWithMessage("Expected an item at slot %d, but none was present. Observed items: %s.", slot, observed);
        return item.orElseThrow();
    }

    private static void validateSlot(int slot)
    {
        if (slot < 0)
            throw new IllegalArgumentException("slot must be >= 0.");
    }

    /** @return The subject after AssertJ's null guard. */
    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the subject.
    protected final A nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
