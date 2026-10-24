package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.EntitySnapshot;
import nl.pim16aap2.lightkeeper.framework.Vec3;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

/** Position and display-transform assertions over one frozen entity observation. */
public final class EntitySnapshotAssert extends AbstractAssert<EntitySnapshotAssert, @Nullable EntitySnapshot>
{
    EntitySnapshotAssert(@Nullable EntitySnapshot actual)
    {
        super(actual, EntitySnapshotAssert.class);
    }

    /**
     * Checks the entity's captured position using an inclusive Euclidean tolerance.
     *
     * @param expected Finite expected position.
     * @param tolerance Finite nonnegative maximum distance.
     * @return This assertion.
     */
    public EntitySnapshotAssert isAt(Vec3 expected, double tolerance)
    {
        position().isCloseTo(expected, tolerance);
        return this;
    }

    /** @return Frozen position assertions retaining the entity identity in diagnostics. */
    public Vec3Assert position()
    {
        return extracting(EntitySnapshot::position, Vec3Assert::new).as(fieldDescription("position"));
    }

    /** @return Frozen display-transform assertions; a non-display entity fails navigation clearly. */
    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the entity snapshot.
    public EntityTransformAssert transform()
    {
        isNotNull();
        if (actual.transform() == null)
            failWithMessage("Expected a display transform for entity '%s', but none was present.", actual.uuid());
        return extracting(EntitySnapshot::transform, EntityTransformAssert::new).as(fieldDescription("transform"));
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // Only invoked after extraction's null guard.
    private String fieldDescription(String field)
    {
        final String context = descriptionText();
        return (context.isEmpty() ? "" : context + " - ") + field + " of entity '" + actual.uuid() + "'";
    }
}
