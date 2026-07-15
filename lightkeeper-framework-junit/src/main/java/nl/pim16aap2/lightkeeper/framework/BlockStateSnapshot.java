package nl.pim16aap2.lightkeeper.framework;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Frozen snapshot of a block's full state at the moment it was read.
 *
 * @param materialKey
 *     Canonical namespaced material key.
 * @param blockData
 *     The full block-data string the server reported (every state property included).
 * @param properties
 *     All state properties, in the server's rendering order.
 */
public record BlockStateSnapshot(
    String materialKey,
    String blockData,
    Map<String, String> properties
)
{
    /**
     * Validates the fields.
     */
    public BlockStateSnapshot
    {
        materialKey = MaterialKeys.normalize(materialKey);
        Objects.requireNonNull(blockData, "blockData may not be null.");
        // An order-preserving copy keeps the documented server rendering order observable.
        properties = properties == null
            ? Map.of()
            : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(properties));
    }

    /**
     * Builds a snapshot from a server-reported block-data string.
     *
     * @param blockData
     *     The full block-data string, e.g. {@code minecraft:lever[face=floor,facing=north,powered=true]}.
     * @return The parsed snapshot.
     */
    static BlockStateSnapshot fromBlockData(String blockData)
    {
        final BlockSpec parsed = BlockSpec.parse(blockData);
        return new BlockStateSnapshot(parsed.materialKey(), blockData, parsed.properties());
    }

    /**
     * Gets a single state property.
     *
     * @param name
     *     The property name, e.g. {@code powered}.
     * @return The property value, or {@code null} when the block has no such property.
     */
    public @Nullable String property(String name)
    {
        return properties.get(name);
    }

    /**
     * Checks whether this state matches a spec: the material must be equal and every property the spec names
     * must have the spec's value. Properties the spec does not name are ignored — partial matching by design.
     *
     * @param spec
     *     The expected spec.
     * @return {@code true} when this state matches.
     */
    public boolean matches(BlockSpec spec)
    {
        Objects.requireNonNull(spec, "spec may not be null.");
        if (!materialKey.equals(spec.materialKey()))
            return false;
        for (final Map.Entry<String, String> expected : spec.properties().entrySet())
        {
            if (!expected.getValue().equals(properties.get(expected.getKey())))
                return false;
        }
        return true;
    }
}
