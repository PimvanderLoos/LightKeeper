package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Shared open-state and title rules for live and frozen menu subjects.
 *
 * @param <S> Concrete assertion type.
 * @param <A> Menu subject type.
 */
abstract class AbstractMenuAssert<S extends AbstractMenuAssert<S, A>, A>
    extends AbstractItemContainerAssert<S, A>
{
    AbstractMenuAssert(@Nullable A actual, Class<?> selfType)
    {
        super(actual, selfType);
    }

    /** @return One live or frozen menu observation. */
    protected abstract MenuSnapshot menuSnapshot();

    /** @return Menu identity for failure diagnostics. */
    protected abstract String menuDescription();

    /** @return This assertion after checking that the menu is open. */
    public S isOpen()
    {
        return hasOpenState(true);
    }

    /** @return This assertion after checking that the retained menu is closed; null subjects fail. */
    public S isClosed()
    {
        return hasOpenState(false);
    }

    /**
     * Checks an open menu's exact title in one observation.
     *
     * @param expectedTitle Expected title.
     * @return This assertion.
     */
    public S hasTitle(String expectedTitle)
    {
        Objects.requireNonNull(expectedTitle, "expectedTitle may not be null.");
        final var observed = openSnapshot();
        if (!expectedTitle.equals(observed.title()))
            failWithMessage("Expected open menu title '%s' for %s, but was '%s'.",
                expectedTitle, menuDescription(), observed.title());
        return myself;
    }

    @Override
    protected final List<MenuItemSnapshot> itemsSnapshot()
    {
        return openSnapshot().items();
    }

    private S hasOpenState(boolean expected)
    {
        final var observed = menuSnapshot();
        if (observed.open() != expected)
            failWithMessage("Expected menu for %s to be %s, but it was %s (title='%s').",
                menuDescription(), expected ? "open" : "closed", observed.open() ? "open" : "closed", observed.title());
        return myself;
    }

    private MenuSnapshot openSnapshot()
    {
        final var observed = menuSnapshot();
        if (!observed.open())
            failWithMessage("Expected an open menu for %s, but it was closed.", menuDescription());
        return observed;
    }
}
