package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.InventorySnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Assertions over a frozen inventory snapshot, including occupied-slot and item navigation. */
public final class InventoryAssert extends AbstractItemContainerAssert<InventoryAssert, InventorySnapshot>
{
    InventoryAssert(@Nullable InventorySnapshot actual)
    {
        super(actual, InventoryAssert.class);
    }

    @Override
    protected List<MenuItemSnapshot> itemsSnapshot()
    {
        return nonNullActual().items();
    }
}
