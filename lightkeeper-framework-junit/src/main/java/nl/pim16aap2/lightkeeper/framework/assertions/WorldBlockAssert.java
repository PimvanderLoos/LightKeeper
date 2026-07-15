package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.BlockSpec;
import nl.pim16aap2.lightkeeper.framework.BlockStateSnapshot;
import nl.pim16aap2.lightkeeper.framework.MaterialKeys;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.assertj.core.api.AbstractAssert;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Assertions for a specific block position in a world.
 */
public final class WorldBlockAssert extends AbstractAssert<WorldBlockAssert, @Nullable WorldHandle>
{
    private final BlockPos position;

    WorldBlockAssert(WorldHandle worldHandle, BlockPos position)
    {
        super(worldHandle, WorldBlockAssert.class);
        this.position = position;
    }

    /**
     * Asserts that the block has the expected Bukkit material.
     *
     * @param material
     *     Expected material.
     * @return This assertion for fluent chaining.
     */
    public WorldBlockAssert ofType(Material material)
    {
        return ofType(material.getKey().toString());
    }

    /**
     * Asserts that the block has the expected namespaced material key.
     *
     * @param materialKey
     *     Expected material key (for example {@code minecraft:stone}).
     * @return This assertion for fluent chaining.
     */
    public WorldBlockAssert ofType(String materialKey)
    {
        final var actual = nonNullActual();
        final String actualMaterial = MaterialKeys.normalize(actual.blockTypeAt(position));
        final String expectedMaterial = MaterialKeys.normalize(materialKey);
        if (!Objects.equals(actualMaterial, expectedMaterial))
        {
            failWithMessage(
                "Expected block at (%d,%d,%d) to be '%s' but was '%s'.",
                position.x(),
                position.y(),
                position.z(),
                expectedMaterial,
                actualMaterial
            );
        }
        return this;
    }

    /**
     * Asserts that the block currently matches a spec: material equal, and every property the spec names has
     * the spec's value (partial matching — unnamed properties are ignored).
     *
     * @param expected
     *     The expected block spec.
     * @return This assertion for fluent chaining.
     */
    public WorldBlockAssert withState(BlockSpec expected)
    {
        Objects.requireNonNull(expected, "expected may not be null.");
        final var actual = nonNullActual();
        final BlockStateSnapshot state = actual.blockAt(position).state();
        if (!state.matches(expected))
        {
            failWithMessage(
                "Expected block at (%d,%d,%d) to match '%s' but was '%s'.",
                position.x(),
                position.y(),
                position.z(),
                expected.asString(),
                state.blockData()
            );
        }
        return this;
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // we call isNotNull() first, so actual is not null after that
    private WorldHandle nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
