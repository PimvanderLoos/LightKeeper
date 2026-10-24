package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Frozen event assertions that preserve typed fields and distinguish absent from explicit-null values. */
public final class CapturedEventAssert extends AbstractAssert<CapturedEventAssert, @Nullable CapturedEventSnapshot>
{
    CapturedEventAssert(@Nullable CapturedEventSnapshot actual)
    {
        super(actual, CapturedEventAssert.class);
    }

    /**
     * Checks whether an accessor was captured, including explicit-null and dropped values.
     *
     * @param accessorName
     *     Exact captured accessor name.
     * @return This assertion.
     */
    public CapturedEventAssert hasField(String accessorName)
    {
        return hasFieldState(accessorName, true);
    }

    /**
     * Checks that an accessor is absent from the capture.
     *
     * @param accessorName
     *     Exact captured accessor name.
     * @return This assertion.
     */
    public CapturedEventAssert doesNotHaveField(String accessorName)
    {
        return hasFieldState(accessorName, false);
    }

    /**
     * Selects a captured typed value, retaining the event class and accessor in diagnostics.
     *
     * @param accessorName
     *     Exact accessor name; missing accessors fail rather than becoming null.
     * @return Frozen typed-value assertions.
     */
    public ProtocolValueAssert value(String accessorName)
    {
        hasField(accessorName);
        final var event = nonNullActual();
        return extracting(
            ignored -> event.value(accessorName),
            value -> new ProtocolValueAssert(value, event.eventClassName() + "." + accessorName)
        );
    }

    /** @return This assertion after checking a present boolean cancellation field is true. */
    public CapturedEventAssert isCancelled()
    {
        value("isCancelled")
            .asBoolean()
            .isTrue();
        return this;
    }

    /** @return This assertion after checking a present boolean cancellation field is false. */
    public CapturedEventAssert isNotCancelled()
    {
        value("isCancelled")
            .asBoolean()
            .isFalse();
        return this;
    }

    private CapturedEventAssert hasFieldState(String name, boolean expected)
    {
        Objects.requireNonNull(name, "accessorName may not be null.");
        if (name.isBlank())
            throw new IllegalArgumentException("accessorName may not be blank.");
        final var event = nonNullActual();
        if (event.values().containsKey(name) != expected)
            failWithMessage(
                "Expected event '%s' to %s field '%s', but captured fields were %s.",
                event.eventClassName(),
                expected ? "have" : "not have",
                name,
                event.values().keySet()
            );
        return this;
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the subject.
    private CapturedEventSnapshot nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
