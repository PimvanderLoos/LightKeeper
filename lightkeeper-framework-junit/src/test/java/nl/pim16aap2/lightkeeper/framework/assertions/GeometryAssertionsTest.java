package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.EntitySnapshot;
import nl.pim16aap2.lightkeeper.framework.EntitySnapshot.Transform;
import nl.pim16aap2.lightkeeper.framework.Vec3;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Tests explicit geometric tolerance contracts and frozen display-transform navigation. */
class GeometryAssertionsTest
{
    @Test
    void isCloseTo_shouldUseInclusiveEuclideanDistance()
    {
        // setup
        final var assertion = assertThat(new Vec3(3, 4, 0));

        // execute
        assertion.isCloseTo(new Vec3(0, 0, 0), 5);
        final Throwable failure = catchThrowable(() -> assertion.isCloseTo(new Vec3(0, 0, 0), 4.9));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("Euclidean", "4.9", "5.0");
    }

    @Test
    void hasComponentsCloseTo_shouldDistinguishScaleToleranceFromDistance()
    {
        // setup
        final var assertion = assertThat(new Vec3(0.1, 0.1, 0.1));
        final var origin = new Vec3(0, 0, 0);

        // execute
        assertion.hasComponentsCloseTo(origin, 0.1);
        final Throwable failure = catchThrowable(() -> assertion.isCloseTo(origin, 0.1));

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
    }

    @Test
    void isCloseTo_shouldAvoidSquaredDistanceOverflow()
    {
        // setup
        final var assertion = assertThat(new Vec3(1e200, 1e200, 1e200));

        // execute
        final var result = assertion.isCloseTo(new Vec3(0, 0, 0), 2e200);

        // verify
        assertThat(result).isSameAs(assertion);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY})
    void isCloseTo_shouldRejectInvalidTolerance(double tolerance)
    {
        // setup
        final var assertion = assertThat(new Vec3(0, 0, 0));

        // execute
        final Throwable failure = catchThrowable(() -> assertion.isCloseTo(new Vec3(0, 0, 0), tolerance));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isCloseTo_shouldRejectNonfiniteExpectedAndReportNonfiniteObservation()
    {
        // setup
        final var origin = new Vec3(0, 0, 0);
        final var invalid = new Vec3(Double.NaN, 0, 0);

        // execute
        final Throwable expectedFailure = catchThrowable(() -> assertThat(origin).isCloseTo(invalid, 1));
        final Throwable observedFailure = catchThrowable(() -> assertThat(invalid).isCloseTo(origin, 1));

        // verify
        assertThat(expectedFailure)
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(observedFailure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("finite observed");
    }

    @Test
    void transform_shouldExposeVectorsAndExactQuaternionValues()
    {
        // setup
        final var rotation = new EntitySnapshot.Rotation(0, 0, 0, 1);
        final var transform = new EntitySnapshot.Transform(new Vec3(1, 2, 3), new Vec3(2, 3, 4), rotation, rotation);
        final var entity = entity(transform);

        // execute
        final var assertion = assertThat(entity)
            .isAt(new Vec3(4, 5, 6), 0)
            .transform();

        // verify
        assertion
            .translation()
            .isCloseTo(new Vec3(1, 2, 3), 0);
        assertion
            .scale()
            .hasComponentsCloseTo(new Vec3(2, 3, 4), 0);
        assertion
            .leftRotation()
            .isEqualTo(rotation);
        assertion
            .rightRotation()
            .isEqualTo(rotation);
    }

    @Test
    void transform_shouldReportEntityIdentityForNonDisplayEntity()
    {
        // setup
        final var entity = new EntitySnapshot(
            UUID.randomUUID(),
            "minecraft:armor_stand",
            new Vec3(0, 0, 0),
            null,
            List.of(),
            null,
            1
        );

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(entity).transform());

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll(entity.uuid().toString(), "none was present");
    }

    @Test
    void isAt_shouldPreserveEntityAndCallerContextWithoutSharingChildDescription()
    {
        // setup
        final var entity = entity(null);
        final var assertion = assertThat(entity).as("Parent context");
        final var position = assertion
            .position()
            .as("Child context");

        // execute
        final Throwable parentFailure = catchThrowable(() -> assertion.isAt(new Vec3(0, 0, 0), 0));
        final Throwable childFailure = catchThrowable(() -> position.isCloseTo(new Vec3(0, 0, 0), 0));

        // verify
        assertThat(parentFailure)
            .hasMessageContainingAll("Parent context", entity.uuid().toString())
            .hasMessageNotContaining("Child context");
        assertThat(childFailure)
            .hasMessageContaining("Child context");
    }

    @Test
    void asVector_shouldReuseToleranceRulesAndRetainEventPath()
    {
        // setup
        final var assertion = new ProtocolValueAssert(new IProtocolValue.PVec(3, 4, 0), "Event.position");

        // execute
        assertion
            .asVector()
            .isCloseTo(new Vec3(0, 0, 0), 5);
        final Throwable failure = catchThrowable(
            () -> assertion
                .asVector()
                .isCloseTo(new Vec3(0, 0, 0), 1)
        );

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Event.position");
    }

    @Test
    void isCloseTo_shouldRejectNullVector()
    {
        // setup
        final var assertion = new Vec3Assert(null);

        // execute
        final Throwable failure = catchThrowable(() -> assertion.isCloseTo(new Vec3(0, 0, 0), 0));

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
    }

    private static EntitySnapshot entity(@Nullable Transform transform)
    {
        return new EntitySnapshot(
            UUID.randomUUID(),
            "minecraft:block_display",
            new Vec3(4, 5, 6),
            null,
            List.of(),
            transform,
            1
        );
    }
}
