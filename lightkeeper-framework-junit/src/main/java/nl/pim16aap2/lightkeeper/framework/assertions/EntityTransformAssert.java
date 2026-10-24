package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.EntitySnapshot;
import nl.pim16aap2.lightkeeper.framework.EntitySnapshot.Transform;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;
import org.jspecify.annotations.Nullable;

/** Frozen display-transform navigation with explicit vector tolerances and ordinary quaternion value checks. */
public final class EntityTransformAssert extends AbstractAssert<EntityTransformAssert, @Nullable Transform>
{
    EntityTransformAssert(@Nullable Transform actual)
    {
        super(actual, EntityTransformAssert.class);
    }

    /** @return Translation assertions; use isCloseTo for Euclidean tolerance. */
    public Vec3Assert translation()
    {
        return extracting(EntitySnapshot.Transform::translation, Vec3Assert::new).as(fieldDescription("translation"));
    }

    /** @return Scale assertions; use hasComponentsCloseTo for per-axis tolerance. */
    public Vec3Assert scale()
    {
        return extracting(EntitySnapshot.Transform::scale, Vec3Assert::new).as(fieldDescription("scale"));
    }

    /** @return Exact/component quaternion assertions; orientation-equivalence semantics remain separate. */
    public ObjectAssert<EntitySnapshot.Rotation> leftRotation()
    {
        return extracting(EntitySnapshot.Transform::leftRotation, Assertions::assertThat)
            .as(fieldDescription("leftRotation"));
    }

    /** @return Exact/component quaternion assertions; these do not normalize rotations. */
    public ObjectAssert<EntitySnapshot.Rotation> rightRotation()
    {
        return extracting(EntitySnapshot.Transform::rightRotation, Assertions::assertThat)
            .as(fieldDescription("rightRotation"));
    }

    private String fieldDescription(String field)
    {
        final String context = descriptionText();
        return context.isEmpty() ? field : context + "." + field;
    }
}
