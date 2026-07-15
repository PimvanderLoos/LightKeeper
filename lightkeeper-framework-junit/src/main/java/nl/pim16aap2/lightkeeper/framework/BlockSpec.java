package nl.pim16aap2.lightkeeper.framework;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A block specification: a material plus the state properties a test cares about.
 *
 * <p>Specs are partial by design: only the named properties participate in matching (see
 * {@link BlockStateSnapshot#matches(BlockSpec)}), so a lever test can assert {@code powered=true} without
 * enumerating {@code face} and {@code facing} defaults.
 *
 * @param materialKey
 *     Canonical namespaced material key, e.g. {@code minecraft:lever}.
 * @param properties
 *     The state properties this spec names, in insertion order.
 */
public record BlockSpec(
    String materialKey,
    Map<String, String> properties
)
{
    /**
     * Validates and normalizes the fields.
     */
    public BlockSpec
    {
        materialKey = MaterialKeys.normalize(materialKey);
        // Vanilla block-state tokens are all lowercase; normalizing here (like the material key) keeps a
        // spec written as 'powered=TRUE' from silently failing to match instead of erroring or working.
        final Map<String, String> normalizedProperties = new LinkedHashMap<>();
        if (properties != null)
        {
            for (final Map.Entry<String, String> property : properties.entrySet())
            {
                if (property.getKey() == null || property.getKey().isBlank())
                    throw new IllegalArgumentException("Property names may not be blank.");
                if (property.getValue() == null || property.getValue().isBlank())
                    throw new IllegalArgumentException(
                        "Property '%s' may not have a blank value.".formatted(property.getKey()));
                normalizedProperties.put(
                    property.getKey().trim().toLowerCase(java.util.Locale.ROOT),
                    property.getValue().trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        properties = Collections.unmodifiableMap(normalizedProperties);
    }

    /**
     * Creates a spec for a material with no state properties.
     *
     * @param material
     *     The material.
     * @return The spec.
     */
    public static BlockSpec of(Material material)
    {
        return of(Objects.requireNonNull(material, "material may not be null.").getKey().toString());
    }

    /**
     * Creates a spec for a material key with no state properties.
     *
     * @param materialKey
     *     Material key, e.g. {@code STONE} or {@code minecraft:stone}.
     * @return The spec.
     */
    public static BlockSpec of(String materialKey)
    {
        return new BlockSpec(materialKey, Map.of());
    }

    /**
     * Parses a block-data style string into a spec.
     *
     * @param blockData
     *     A string like {@code minecraft:lever[powered=true,facing=north]}; the bracket section is optional.
     * @return The parsed spec.
     * @throws IllegalArgumentException
     *     If the string is malformed.
     */
    public static BlockSpec parse(String blockData)
    {
        final String trimmed = Objects.requireNonNull(blockData, "blockData may not be null.").trim();
        final int bracketStart = trimmed.indexOf('[');
        if (bracketStart < 0)
            return of(trimmed);
        if (!trimmed.endsWith("]"))
            throw new IllegalArgumentException(
                "Malformed block data '%s': missing closing bracket.".formatted(blockData));

        final String materialPart = trimmed.substring(0, bracketStart);
        final String propertiesPart = trimmed.substring(bracketStart + 1, trimmed.length() - 1).trim();
        final Map<String, String> parsedProperties = new LinkedHashMap<>();
        if (!propertiesPart.isEmpty())
        {
            for (final String entry : propertiesPart.split(",", -1))
            {
                final int equalsIndex = entry.indexOf('=');
                if (equalsIndex <= 0 || equalsIndex == entry.length() - 1)
                    throw new IllegalArgumentException(
                        "Malformed block data '%s': property '%s' is not a key=value pair."
                            .formatted(blockData, entry.trim()));
                final String previous = parsedProperties.put(
                    entry.substring(0, equalsIndex).trim(),
                    entry.substring(equalsIndex + 1).trim());
                if (previous != null)
                    throw new IllegalArgumentException(
                        "Malformed block data '%s': duplicate property '%s'."
                            .formatted(blockData, entry.substring(0, equalsIndex).trim()));
            }
        }
        return new BlockSpec(materialPart, parsedProperties);
    }

    /**
     * Returns a copy of this spec with one property set.
     *
     * @param property
     *     The property name.
     * @param value
     *     The property value.
     * @return A new spec with the property applied.
     */
    public BlockSpec with(String property, String value)
    {
        final Map<String, String> extendedProperties = new LinkedHashMap<>(properties);
        extendedProperties.put(property, value);
        return new BlockSpec(materialKey, extendedProperties);
    }

    /**
     * Renders this spec in block-data string form.
     *
     * @return E.g. {@code minecraft:lever[powered=true]}; just the material key when no properties are named.
     */
    public String asString()
    {
        if (properties.isEmpty())
            return materialKey;
        return properties.entrySet().stream()
            .map(property -> property.getKey() + "=" + property.getValue())
            .collect(Collectors.joining(",", materialKey + "[", "]"));
    }
}
