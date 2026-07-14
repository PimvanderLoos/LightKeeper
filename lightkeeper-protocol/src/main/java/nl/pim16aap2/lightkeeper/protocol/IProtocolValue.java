package nl.pim16aap2.lightkeeper.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Typed wire envelope for structured payload values.
 *
 * <p>Every value crossing the agent protocol as part of a structured payload (e.g. captured event fields) is
 * one of these leaves. The envelope replaces flat string-only payloads: consumers receive real types, nesting
 * is explicit ({@link PList}, {@link PRecord}), identities travel as references ({@link PRef}), and values the
 * encoder cannot represent are reported as {@link PDropped} markers instead of being silently skipped.
 *
 * <p>Jackson uses the {@code "type"} JSON property to select the correct leaf during deserialization; adding a
 * leaf requires a new {@link JsonSubTypes.Type} entry and a {@code permits} entry here.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = IProtocolValue.PString.class, name = "STRING"),
    @JsonSubTypes.Type(value = IProtocolValue.PNumber.class, name = "NUMBER"),
    @JsonSubTypes.Type(value = IProtocolValue.PBool.class, name = "BOOL"),
    @JsonSubTypes.Type(value = IProtocolValue.PUuid.class, name = "UUID"),
    @JsonSubTypes.Type(value = IProtocolValue.PEnum.class, name = "ENUM"),
    @JsonSubTypes.Type(value = IProtocolValue.PList.class, name = "LIST"),
    @JsonSubTypes.Type(value = IProtocolValue.PRecord.class, name = "RECORD"),
    @JsonSubTypes.Type(value = IProtocolValue.PRef.class, name = "REF"),
    @JsonSubTypes.Type(value = IProtocolValue.PDropped.class, name = "DROPPED"),
})
public sealed interface IProtocolValue
    permits IProtocolValue.PString, IProtocolValue.PNumber, IProtocolValue.PBool, IProtocolValue.PUuid,
            IProtocolValue.PEnum, IProtocolValue.PList, IProtocolValue.PRecord, IProtocolValue.PRef,
            IProtocolValue.PDropped
{
    /**
     * Renders this value as human-readable text for assertion messages and diagnostics.
     *
     * @return The rendered value.
     */
    String toDisplayString();

    /**
     * A text value.
     *
     * @param value
     *     The text.
     */
    record PString(String value) implements IProtocolValue
    {
        /**
         * Validates the value.
         */
        public PString
        {
            ProtocolPreconditions.requireNonNull(value, "value");
        }

        @Override
        public String toDisplayString()
        {
            return value;
        }
    }

    /**
     * A numeric value.
     *
     * <p>Integral inputs are normalized to {@link Long} and floating-point inputs to {@link Double}, so equal
     * numbers compare equal regardless of the boxed type they were encoded or deserialized from. Arbitrary
     * precision types ({@code BigInteger}, {@code BigDecimal}) are coerced to {@code double} and may lose
     * precision; no supported payload source produces them today.
     *
     * @param value
     *     The number; a {@link Long} for integral values, a {@link Double} for floating-point values.
     */
    record PNumber(Number value) implements IProtocolValue
    {
        /**
         * Validates and normalizes the value.
         */
        public PNumber
        {
            ProtocolPreconditions.requireNonNull(value, "value");
            if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long)
                value = value.longValue();
            else
                value = value.doubleValue();
        }

        @Override
        public String toDisplayString()
        {
            return String.valueOf(value);
        }
    }

    /**
     * A boolean value.
     *
     * @param value
     *     The boolean.
     */
    record PBool(boolean value) implements IProtocolValue
    {
        @Override
        public String toDisplayString()
        {
            return String.valueOf(value);
        }
    }

    /**
     * A UUID value.
     *
     * @param value
     *     The UUID.
     */
    record PUuid(UUID value) implements IProtocolValue
    {
        /**
         * Validates the value.
         */
        public PUuid
        {
            ProtocolPreconditions.requireNonNull(value, "value");
        }

        @Override
        public String toDisplayString()
        {
            return value.toString();
        }
    }

    /**
     * An enum constant.
     *
     * @param enumClass
     *     Fully-qualified class name of the enum type.
     * @param name
     *     The constant's name.
     */
    record PEnum(String enumClass, String name) implements IProtocolValue
    {
        /**
         * Validates the fields.
         */
        public PEnum
        {
            ProtocolPreconditions.requireNonBlank(enumClass, "enumClass");
            ProtocolPreconditions.requireNonBlank(name, "name");
        }

        @Override
        public String toDisplayString()
        {
            return name;
        }
    }

    /**
     * An ordered list of values.
     *
     * @param values
     *     The list elements.
     */
    record PList(List<IProtocolValue> values) implements IProtocolValue
    {
        /**
         * Validates and defensively copies the elements.
         */
        public PList
        {
            values = values == null ? List.of() : List.copyOf(values);
        }

        @Override
        public String toDisplayString()
        {
            return values.stream()
                .map(IProtocolValue::toDisplayString)
                .collect(Collectors.joining(", ", "[", "]"));
        }
    }

    /**
     * A nested structured value: named fields, insertion-ordered.
     *
     * @param fields
     *     The field values by name.
     */
    record PRecord(Map<String, IProtocolValue> fields) implements IProtocolValue
    {
        /**
         * Validates and defensively copies the fields, preserving their order.
         */
        public PRecord
        {
            // Map.copyOf would discard iteration order; an unmodifiable LinkedHashMap copy keeps the
            // encoder's field order observable on the consumer side.
            fields = fields == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
        }

        @Override
        public String toDisplayString()
        {
            return fields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().toDisplayString())
                .collect(Collectors.joining(", ", "{", "}"));
        }
    }

    /**
     * A reference to an identifiable object (e.g. an entity by UUID or a world by name) rather than its
     * contents.
     *
     * @param className
     *     Fully-qualified class name of the referenced object.
     * @param id
     *     The reference identity, e.g. an entity UUID or a world name.
     */
    record PRef(String className, String id) implements IProtocolValue
    {
        /**
         * Validates the fields.
         */
        public PRef
        {
            ProtocolPreconditions.requireNonBlank(className, "className");
            ProtocolPreconditions.requireNonBlank(id, "id");
        }

        @Override
        public String toDisplayString()
        {
            return className + "@" + id;
        }
    }

    /**
     * A marker for a value the encoder could not represent — the fail-loud replacement for silently skipping
     * it. The accessor's presence with this marker tells the consumer exactly what was lost and why.
     *
     * @param accessorName
     *     Name of the accessor whose value was dropped.
     * @param runtimeType
     *     The runtime type (or failure description) of the dropped value.
     */
    record PDropped(String accessorName, String runtimeType) implements IProtocolValue
    {
        /**
         * Validates the fields.
         */
        public PDropped
        {
            ProtocolPreconditions.requireNonBlank(accessorName, "accessorName");
            ProtocolPreconditions.requireNonBlank(runtimeType, "runtimeType");
        }

        @Override
        public String toDisplayString()
        {
            return "<dropped: %s (%s)>".formatted(accessorName, runtimeType);
        }
    }
}
