package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.Vec3;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractBooleanAssert;
import org.assertj.core.api.AbstractComparableAssert;
import org.assertj.core.api.AbstractDoubleAssert;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.MapAssert;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Typed, frozen value navigation that preserves accessor/index paths without string coercion. */
public final class ProtocolValueAssert extends AbstractAssert<ProtocolValueAssert, @Nullable IProtocolValue>
{
    /** Captured accessor/index path used in diagnostics. */
    private final String path;

    ProtocolValueAssert(@Nullable IProtocolValue actual)
    {
        this(actual, "value");
    }

    ProtocolValueAssert(@Nullable IProtocolValue actual, String path)
    {
        super(actual, ProtocolValueAssert.class);
        this.path = path;
    }

    /** @return Assertions over a PString; other variants fail rather than being stringified. */
    @Override
    public AbstractStringAssert<?> asString()
    {
        final String value = requireType(IProtocolValue.PString.class).value();
        return extracting(ignored -> value, Assertions::assertThat)
            .as(valueDescription());
    }

    /** @return Assertions over a PBool; missing/null/dropped values are not false. */
    public AbstractBooleanAssert<?> asBoolean()
    {
        final boolean value = requireType(IProtocolValue.PBool.class).value();
        return extracting(ignored -> value, Assertions::assertThat)
            .as(valueDescription());
    }

    /** @return Exact integral-number assertions, preserving all 64 bits. */
    public AbstractLongAssert<?> asLong()
    {
        final Number number = requireType(IProtocolValue.PNumber.class).value();
        if (!(number instanceof Long))
        {
            failWithMessage("%s: expected an integral number, but was %s.", path, number);
        }

        return extracting(ignored -> number.longValue(), Assertions::assertThat).as(valueDescription());
    }

    /** @return Floating-number assertions; integral values are not silently coerced. */
    public AbstractDoubleAssert<?> asDouble()
    {
        final Number number = requireType(IProtocolValue.PNumber.class).value();
        if (!(number instanceof Double))
        {
            failWithMessage("%s: expected a floating number, but was %s.", path, number);
        }

        return extracting(ignored -> number.doubleValue(), Assertions::assertThat).as(valueDescription());
    }

    /** @return Ordinary UUID assertions over a PUuid. */
    public AbstractComparableAssert<?, UUID> asUuid()
    {
        final UUID value = requireType(IProtocolValue.PUuid.class).value();
        return extracting(ignored -> value, Assertions::assertThat)
            .as(valueDescription());
    }

    /**
     * Compares both enum type and constant without loading a server-only class.
     *
     * @param enumClass
     *     Fully qualified enum type.
     * @param name
     *     Constant name.
     * @return This assertion.
     */
    public ProtocolValueAssert hasEnum(String enumClass, String name)
    {
        final var expected = new IProtocolValue.PEnum(enumClass, name);
        final var observed = requireType(IProtocolValue.PEnum.class);
        if (!expected.equals(observed))
        {
            failWithMessage("%s: expected enum %s, but was %s.", path, expected, observed);
        }
        return this;
    }

    /**
     * Compares the encoded reference type and identity.
     *
     * @param className
     *     Encoded referenced class.
     * @param id
     *     Encoded reference identity.
     * @return This assertion.
     */
    public ProtocolValueAssert hasReference(String className, String id)
    {
        final var expected = new IProtocolValue.PRef(className, id);
        final var observed = requireType(IProtocolValue.PRef.class);
        if (!expected.equals(observed))
        {
            failWithMessage("%s: expected reference %s, but was %s.", path, expected, observed);
        }
        return this;
    }

    /** @return Ordinary list assertions over frozen PList contents. */
    public ListAssert<IProtocolValue> elements()
    {
        final var values = requireType(IProtocolValue.PList.class).values();
        return extracting(ignored -> values, Assertions::assertThat)
            .as(valueDescription());
    }

    /**
     * Selects a nested list element and retains its index in diagnostics.
     *
     * @param index
     *     Nonnegative element index; out-of-range observations fail as assertions.
     * @return Nested typed assertions.
     */
    public ProtocolValueAssert element(int index)
    {
        if (index < 0)
        {
            throw new IllegalArgumentException("index must be >= 0.");
        }

        final var values = requireType(IProtocolValue.PList.class).values();
        if (index >= values.size())
        {
            failWithMessage("%s[%d]: expected an element, but list size was %d.", path, index, values.size());
        }

        return extracting(
            ignored -> values.get(index),
            value -> new ProtocolValueAssert(value, path + "[" + index + "]")
        );
    }

    /** @return Ordinary map assertions over frozen PRecord fields. */
    public MapAssert<String, IProtocolValue> fields()
    {
        final var values = requireType(IProtocolValue.PRecord.class).fields();
        return extracting(ignored -> values, Assertions::assertThat)
            .as(valueDescription());
    }

    /**
     * Selects a record field, distinguishing a missing key from explicit null.
     *
     * @param name
     *     Exact field name.
     * @return Nested typed assertions.
     */
    public ProtocolValueAssert field(String name)
    {
        Objects.requireNonNull(name, "name may not be null.");
        if (name.isBlank())
        {
            throw new IllegalArgumentException("name may not be blank.");
        }

        final var values = requireType(IProtocolValue.PRecord.class).fields();
        if (!values.containsKey(name))
        {
            failWithMessage("%s.%s: expected a field, but keys were %s.", path, name, values.keySet());
        }

        return extracting(ignored -> values.get(name), value -> new ProtocolValueAssert(value, path + "." + name));
    }

    /** @return This assertion after verifying an explicit unavailable-data marker. */
    public ProtocolValueAssert isDropped()
    {
        requireType(IProtocolValue.PDropped.class);
        return this;
    }

    /** @return Frozen vector assertions over a PVec using the shared tolerance contracts. */
    public Vec3Assert asVector()
    {
        final var value = requireType(IProtocolValue.PVec.class);
        return extracting(ignored -> new Vec3(value.x(), value.y(), value.z()), Vec3Assert::new)
            .as(valueDescription());
    }

    private <T extends IProtocolValue> T requireType(Class<T> expected)
    {
        if (!expected.isInstance(actual))
        {
            failWithMessage("%s: expected %s, but was %s.", path, expected.getSimpleName(), String.valueOf(actual));
        }

        return Objects.requireNonNull(expected.cast(actual));
    }

    private String valueDescription()
    {
        final String description = descriptionText();
        return description.isEmpty() ? path : description + " (" + path + ")";
    }
}
