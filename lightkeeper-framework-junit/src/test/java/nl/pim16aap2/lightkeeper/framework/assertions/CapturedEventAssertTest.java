package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Tests typed cancellation and captured-field presence semantics. */
class CapturedEventAssertTest
{
    @Test
    void cancellation_shouldReadPresentBooleanValues()
    {
        // setup
        final var cancelled = event(new IProtocolValue.PBool(true));
        final var uncancelled = event(new IProtocolValue.PBool(false));

        // execute
        final var first = assertThat(cancelled).isCancelled();
        final var second = assertThat(uncancelled).isNotCancelled();

        // verify
        first.hasField("isCancelled").doesNotHaveField("other");
        second.value("isCancelled").asBoolean().isFalse();
    }

    @ParameterizedTest
    @MethodSource("unavailableBooleans")
    void isNotCancelled_shouldRejectWrongTypeOrDroppedValue(IProtocolValue value)
    {
        // setup
        final var assertion = assertThat(event(value));

        // execute
        final Throwable failure = catchThrowable(assertion::isNotCancelled);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContainingAll("Event.isCancelled", "PBool");
    }

    @Test
    void isNotCancelled_shouldRejectMissingCancellationField()
    {
        // setup
        final var assertion = assertThat(new CapturedEventSnapshot("Event", 1, Map.of()));

        // execute
        final Throwable failure = catchThrowable(assertion::isNotCancelled);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContainingAll("Event", "isCancelled");
    }

    @Test
    @SuppressWarnings("NullAway") // Explicit-null values are distinct from absent accessors.
    void value_shouldDistinguishNullAndMissingAccessors()
    {
        // setup
        final var values = new LinkedHashMap<String, IProtocolValue>();
        values.put("nullable", null);
        final var assertion = assertThat(new CapturedEventSnapshot("Event", 1, values));

        // execute
        final var present = assertion.hasField("nullable").value("nullable");
        final Throwable failure = catchThrowable(() -> assertion.value("missing"));

        // verify
        present.isNull();
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContainingAll("Event", "missing");
    }

    @Test
    void hasField_shouldRejectBlankAccessor()
    {
        // setup
        final var assertion = assertThat(new CapturedEventSnapshot("Event", 1, Map.of()));

        // execute
        final Throwable failure = catchThrowable(() -> assertion.hasField(" "));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    }

    private static CapturedEventSnapshot event(IProtocolValue value)
    {
        return new CapturedEventSnapshot("Event", 1, Map.of("isCancelled", value));
    }

    private static Stream<IProtocolValue> unavailableBooleans()
    {
        return Stream.of(new IProtocolValue.PString("false"), new IProtocolValue.PDropped("isCancelled", "Unavailable"));
    }
}
