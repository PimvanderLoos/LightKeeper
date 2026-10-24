package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Tests strict typed navigation, nested paths and unavailable-data diagnostics. */
class ProtocolValueAssertTest
{
    @Test
    void scalarAssertions_shouldPreserveValuesWithoutStringCoercion()
    {
        // setup
        final UUID id = UUID.randomUUID();

        // execute
        final var text = assertThat(new IProtocolValue.PString("hello")).asString();
        final var bool = assertThat(new IProtocolValue.PBool(true)).asBoolean();
        final var integral = assertThat(new IProtocolValue.PNumber(Long.MAX_VALUE)).asLong();
        final var floating = assertThat(new IProtocolValue.PNumber(0.25)).asDouble();
        final var uuid = assertThat(new IProtocolValue.PUuid(id)).asUuid();

        // verify
        text.isEqualTo("hello");
        bool.isTrue();
        integral.isEqualTo(Long.MAX_VALUE);
        floating.isEqualTo(0.25);
        uuid.isEqualTo(id);
    }

    @ParameterizedTest
    @MethodSource("nonBooleanValues")
    void asBoolean_shouldRejectWrongVariantsIncludingDroppedValues(IProtocolValue value)
    {
        // setup
        final var assertion = new ProtocolValueAssert(value, "Event.field");

        // execute
        final Throwable failure = catchThrowable(assertion::asBoolean);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContainingAll("Event.field", "PBool");
    }

    @Test
    void numberAssertions_shouldRejectImplicitIntegralFloatingConversion()
    {
        // setup
        final var integral = assertThat(new IProtocolValue.PNumber(1));
        final var floating = assertThat(new IProtocolValue.PNumber(1.0));

        // execute
        final Throwable longFailure = catchThrowable(floating::asLong);
        final Throwable doubleFailure = catchThrowable(integral::asDouble);

        // verify
        assertThat(longFailure).isInstanceOf(AssertionError.class).hasMessageContaining("integral");
        assertThat(doubleFailure).isInstanceOf(AssertionError.class).hasMessageContaining("floating");
    }

    @Test
    void hasEnum_shouldMatchDeclaringTypeAndConstantWithoutClassLoading()
    {
        // setup
        final var assertion = assertThat(new IProtocolValue.PEnum("server.only.Action", "OPEN"));

        // execute
        assertion.hasEnum("server.only.Action", "OPEN");
        final Throwable failure = catchThrowable(() -> assertion.hasEnum("other.Action", "OPEN"));

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContainingAll("server.only.Action", "other.Action");
    }

    @Test
    void hasReference_shouldCompareBothTypeAndIdentity()
    {
        // setup
        final var assertion = assertThat(new IProtocolValue.PRef("Player", "player-1"));

        // execute
        assertion.hasReference("Player", "player-1");
        final Throwable failure = catchThrowable(() -> assertion.hasReference("World", "player-1"));

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContainingAll("Player", "World");
    }

    @Test
    void fieldAndElement_shouldRetainFullAccessorPath()
    {
        // setup
        final var value = new IProtocolValue.PRecord(Map.of(
            "items", new IProtocolValue.PList(List.of(new IProtocolValue.PString("stone")))));
        final var assertion = new ProtocolValueAssert(value, "Event.payload").as("Action result");

        // execute
        final var selected = assertion.field("items").element(0);
        final Throwable failure = catchThrowable(() -> selected.asString().isEqualTo("dirt"));

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("Action result", "Event.payload.items[0]", "stone", "dirt");
        assertion.fields().containsKey("items");
        assertion.field("items").elements().hasSize(1);
    }

    @Test
    void element_shouldReportObservedBounds()
    {
        // setup
        final var assertion = assertThat(new IProtocolValue.PList(List.of()));

        // execute
        final Throwable failure = catchThrowable(() -> assertion.element(0));
        final Throwable invalid = catchThrowable(() -> assertion.element(-1));

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContainingAll("[0]", "size was 0");
        assertThat(invalid).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // Explicit-null protocol entries are part of the wire contract.
    void field_shouldDistinguishExplicitNullFromMissingField()
    {
        // setup
        final var values = new LinkedHashMap<String, IProtocolValue>();
        values.put("nullable", null);
        final var assertion = assertThat(new IProtocolValue.PRecord(values));

        // execute
        final var present = assertion.field("nullable");
        final Throwable missing = catchThrowable(() -> assertion.field("missing"));
        final Throwable wrongType = catchThrowable(present::asBoolean);

        // verify
        present.isNull();
        assertThat(missing).isInstanceOf(AssertionError.class).hasMessageContaining("value.missing");
        assertThat(wrongType).isInstanceOf(AssertionError.class).hasMessageContainingAll("value.nullable", "null");
    }

    @Test
    void isDropped_shouldReportWhyTypedUnwrappingIsUnavailable()
    {
        // setup
        final var assertion = assertThat(new IProtocolValue.PDropped("getUnknown", "unsupported.Type"));

        // execute
        assertion.isDropped();
        final Throwable failure = catchThrowable(assertion::asString);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("getUnknown", "unsupported.Type", "PString");
    }

    @Test
    void asUuid_shouldRejectNullValueWithAccessorContext()
    {
        // setup
        final var assertion = new ProtocolValueAssert(null, "Event.id");

        // execute
        final Throwable failure = catchThrowable(assertion::asUuid);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContainingAll("Event.id", "null");
    }

    private static Stream<IProtocolValue> nonBooleanValues()
    {
        return Stream.of(new IProtocolValue.PString("false"), new IProtocolValue.PNumber(0),
            new IProtocolValue.PUuid(UUID.randomUUID()), new IProtocolValue.PEnum("Type", "FALSE"),
            new IProtocolValue.PList(List.of()), new IProtocolValue.PRecord(Map.of()),
            new IProtocolValue.PRef("Player", "id"), new IProtocolValue.PVec(0, 0, 0),
            new IProtocolValue.PDropped("field", "Type"));
    }
}
