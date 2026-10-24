package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Assertions over the material, display name and ordered lore of one frozen item observation. */
public final class MenuItemSnapshotAssert extends AbstractAssert<MenuItemSnapshotAssert, @Nullable MenuItemSnapshot>
{
    MenuItemSnapshotAssert(@Nullable MenuItemSnapshot actual)
    {
        super(actual, MenuItemSnapshotAssert.class);
    }

    /**
     * Checks the item's normalized material.
     *
     * @param expected
     *     Material name or namespaced key.
     * @return This assertion.
     */
    public MenuItemSnapshotAssert isMaterial(String expected)
    {
        final var observed = nonNullActual();
        if (!observed.matchesMaterial(expected))
        {
            failWithMessage(
                "Expected item at slot %d to have material '%s', but was '%s'.",
                observed.slot(),
                expected,
                observed.materialKey()
            );
        }
        return this;
    }

    /**
     * Checks the exact display name; a blank expected name means no custom name.
     *
     * @param expected
     *     Expected display name.
     * @return This assertion.
     */
    public MenuItemSnapshotAssert hasDisplayName(String expected)
    {
        Objects.requireNonNull(expected, "expected may not be null.");
        displayName().isEqualTo(expected.isBlank() ? "" : expected);
        return this;
    }

    /** @return Ordinary assertions over the captured display name. */
    public AbstractStringAssert<?> displayName()
    {
        return extracting(MenuItemSnapshot::displayName, Assertions::assertThat);
    }

    /** @return Ordinary list assertions over captured lore; use containsExactly for ordered equality. */
    public ListAssert<String> lore()
    {
        return extracting(MenuItemSnapshot::lore, Assertions::assertThat);
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the subject.
    private MenuItemSnapshot nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
