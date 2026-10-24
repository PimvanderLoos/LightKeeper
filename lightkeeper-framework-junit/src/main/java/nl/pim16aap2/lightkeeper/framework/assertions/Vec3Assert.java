package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.Vec3;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Frozen vector assertions with explicit Euclidean and per-component tolerance contracts. */
public final class Vec3Assert extends AbstractAssert<Vec3Assert, @Nullable Vec3>
{
    Vec3Assert(@Nullable Vec3 actual)
    {
        super(actual, Vec3Assert.class);
    }

    /**
     * Checks an inclusive Euclidean distance bound.
     *
     * @param expected
     *     Finite target vector.
     * @param tolerance
     *     Finite nonnegative maximum distance.
     * @return This assertion.
     */
    public Vec3Assert isCloseTo(Vec3 expected, double tolerance)
    {
        return compare(expected, tolerance, false);
    }

    /**
     * Checks an inclusive absolute-error bound on each component, suitable for scale vectors.
     *
     * @param expected
     *     Finite target vector.
     * @param tolerance
     *     Finite nonnegative maximum error per component.
     * @return This assertion.
     */
    public Vec3Assert hasComponentsCloseTo(Vec3 expected, double tolerance)
    {
        return compare(expected, tolerance, true);
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the observed vector.
    private Vec3Assert compare(Vec3 expected, double tolerance, boolean componentWise)
    {
        Objects.requireNonNull(expected, "expected may not be null.");
        if (!finite(expected) || !Double.isFinite(tolerance) || tolerance < 0)
            throw new IllegalArgumentException(
                "Expected coordinates and tolerance must be finite; tolerance must be >= 0."
            );
        isNotNull();
        if (!finite(actual))
            failWithMessage("Expected finite observed coordinates, but was %s.", actual);
        final double dx = Math.abs(actual.x() - expected.x());
        final double dy = Math.abs(actual.y() - expected.y());
        final double dz = Math.abs(actual.z() - expected.z());
        final double error = componentWise ? Math.max(dx, Math.max(dy, dz)) : Math.hypot(Math.hypot(dx, dy), dz);
        if (error > tolerance)
            failWithMessage(
                "Expected vector %s within %s %s of %s, but deviation was %s.",
                actual,
                tolerance,
                componentWise ? "per component" : "Euclidean distance",
                expected,
                error
            );
        return this;
    }

    private static boolean finite(Vec3 vector)
    {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }
}
