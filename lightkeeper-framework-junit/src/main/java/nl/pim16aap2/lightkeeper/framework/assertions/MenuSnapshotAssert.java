package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import org.jspecify.annotations.Nullable;

/** Assertions over one frozen menu observation; these never query the server. */
public final class MenuSnapshotAssert extends AbstractMenuAssert<MenuSnapshotAssert, MenuSnapshot>
{
    MenuSnapshotAssert(@Nullable MenuSnapshot actual)
    {
        super(actual, MenuSnapshotAssert.class);
    }

    @Override
    protected MenuSnapshot menuSnapshot()
    {
        return nonNullActual();
    }

    @Override
    protected String menuDescription()
    {
        return "captured snapshot";
    }
}
