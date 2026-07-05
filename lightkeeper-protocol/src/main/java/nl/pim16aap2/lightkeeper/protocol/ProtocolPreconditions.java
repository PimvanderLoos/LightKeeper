package nl.pim16aap2.lightkeeper.protocol;

import org.jspecify.annotations.Nullable;

/**
 * Validation helpers for protocol command compact constructors.
 *
 * <p>Wire requests are deserialized straight into records, bypassing NullAway, so a malformed request can put
 * {@code null}/garbage into a non-{@code @Nullable} field. Validating in the compact constructor rejects such
 * input up front with a consistent {@link IllegalArgumentException} (which the dispatcher maps to
 * {@code INVALID_ARGUMENT}) instead of letting it fail deep inside a server handler with an opaque error.
 *
 * <p>Example:
 * <pre>{@code
 * public Command
 * {
 *     ProtocolPreconditions.requireNonBlank(requestId, "requestId");
 *     ProtocolPreconditions.requireNonNull(uuid, "uuid");
 * }
 * }</pre>
 */
public final class ProtocolPreconditions
{
    private ProtocolPreconditions()
    {
    }

    /**
     * Requires that a string is neither {@code null} nor blank.
     *
     * @param value
     *     The value to check.
     * @param name
     *     Field name used in the failure message.
     * @return The validated value.
     * @throws IllegalArgumentException
     *     When {@code value} is {@code null} or blank.
     */
    public static String requireNonBlank(@Nullable String value, String name)
    {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("'%s' must not be blank.".formatted(name));
        return value;
    }

    /**
     * Requires that a reference is non-{@code null}.
     *
     * @param value
     *     The value to check.
     * @param name
     *     Field name used in the failure message.
     * @param <T>
     *     The value type.
     * @return The validated value.
     * @throws IllegalArgumentException
     *     When {@code value} is {@code null}.
     */
    public static <T> T requireNonNull(@Nullable T value, String name)
    {
        if (value == null)
            throw new IllegalArgumentException("'%s' must not be null.".formatted(name));
        return value;
    }

    /**
     * Requires that an integer is not negative.
     *
     * @param value
     *     The value to check.
     * @param name
     *     Field name used in the failure message.
     * @return The validated value.
     * @throws IllegalArgumentException
     *     When {@code value} is negative.
     */
    public static int requireNonNegative(int value, String name)
    {
        if (value < 0)
            throw new IllegalArgumentException("'%s' must be >= 0, got: %d".formatted(name, value));
        return value;
    }

    /**
     * Requires that a coordinate is a finite number (not NaN or infinite).
     *
     * @param value
     *     The value to check.
     * @param name
     *     Field name used in the failure message.
     * @return The validated value.
     * @throws IllegalArgumentException
     *     When {@code value} is NaN or infinite.
     */
    public static double requireFinite(double value, String name)
    {
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("'%s' must be a finite number, got: %s".formatted(name, value));
        return value;
    }
}
