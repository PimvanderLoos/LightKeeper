package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.EntityQuery;
import nl.pim16aap2.lightkeeper.framework.EntitySnapshot;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;

/** Live entity-query assertions; count checks use the cheap count-only RPC and never load chunks. */
public final class EntityQueryAssert extends AbstractAssert<EntityQueryAssert, @Nullable EntityQuery>
{
    EntityQueryAssert(@Nullable EntityQuery actual)
    {
        super(actual, EntityQueryAssert.class);
    }

    /**
     * Checks the current number of matching entities.
     *
     * @param expected Nonnegative expected count.
     * @return This assertion.
     */
    public EntityQueryAssert hasCount(int expected)
    {
        if (expected < 0)
            throw new IllegalArgumentException("expected must be >= 0.");
        final var query = nonNullActual();
        final int observed = query.count();
        if (observed != expected)
            failWithMessage("Expected entity query (%s) to match %d entities, but matched %d.",
                query.description(), expected, observed);
        return this;
    }

    /** @return This assertion after checking zero matches in currently loaded chunks. */
    public EntityQueryAssert isEmpty()
    {
        return hasCount(0);
    }

    /** @return This assertion after checking at least one current match. */
    public EntityQueryAssert isNotEmpty()
    {
        final var query = nonNullActual();
        final int observed = query.count();
        if (observed <= 0)
            failWithMessage("Expected entity query (%s) to match at least one entity, but matched %d.",
                query.description(), observed);
        return this;
    }

    /** @return One frozen, internally consistent burst of entity snapshots. */
    public ListAssert<EntitySnapshot> snapshot()
    {
        return extracting(EntityQuery::snapshot, Assertions::assertThat);
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the query.
    private EntityQuery nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
