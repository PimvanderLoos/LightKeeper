package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import org.jspecify.annotations.Nullable;

/** Eager assertions over the current menu for a retained player-bound handle. */
public final class MenuHandleAssert extends AbstractMenuAssert<MenuHandleAssert, MenuHandle>
{
    MenuHandleAssert(@Nullable MenuHandle actual)
    {
        super(actual, MenuHandleAssert.class);
    }

    /** @return Frozen assertions over one menu observation; recreate navigation when retrying. */
    public MenuSnapshotAssert snapshot()
    {
        return extracting(MenuHandle::snapshot, MenuSnapshotAssert::new);
    }

    @Override
    protected MenuSnapshot menuSnapshot()
    {
        return nonNullActual().snapshot();
    }

    @Override
    protected String menuDescription()
    {
        return "player '" + nonNullActual()
            .player()
            .name() + "'";
    }
}
