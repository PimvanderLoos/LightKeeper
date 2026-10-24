package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.BlockRef;
import nl.pim16aap2.lightkeeper.framework.BlockSpec;
import nl.pim16aap2.lightkeeper.framework.MaterialKeys;
import org.assertj.core.api.AbstractAssert;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Eager assertions over a live block address, using the canonical material and partial-state rules. */
public final class BlockRefAssert extends AbstractAssert<BlockRefAssert, @Nullable BlockRef>
{
    BlockRefAssert(@Nullable BlockRef actual)
    {
        super(actual, BlockRefAssert.class);
    }

    /**
     * Checks a block material with one live read.
     *
     * @param expected Expected Bukkit material.
     * @return This assertion.
     */
    public BlockRefAssert ofType(Material expected)
    {
        return ofType(Objects.requireNonNull(expected, "expected may not be null.").getKey().toString());
    }

    /**
     * Checks a normalized block material with one live read.
     *
     * @param expected Material name or namespaced key.
     * @return This assertion.
     */
    public BlockRefAssert ofType(String expected)
    {
        final String normalized = MaterialKeys.normalize(expected);
        final var block = nonNullActual();
        final String observed = block.material();
        if (!normalized.equals(observed))
            failWithMessage("Expected block at (%d,%d,%d) in world '%s' to be '%s' but was '%s'.",
                block.pos().x(), block.pos().y(), block.pos().z(), block.worldName(), normalized, observed);
        return this;
    }

    /**
     * Checks material and only the state properties named by the spec, reading one snapshot.
     *
     * @param expected Partial expected block state.
     * @return This assertion.
     */
    public BlockRefAssert withState(BlockSpec expected)
    {
        Objects.requireNonNull(expected, "expected may not be null.");
        final var block = nonNullActual();
        final var observed = block.state();
        if (!observed.matches(expected))
            failWithMessage("Expected block at (%d,%d,%d) in world '%s' to match '%s' but was '%s'.",
                block.pos().x(), block.pos().y(), block.pos().z(), block.worldName(),
                expected.asString(), observed.blockData());
        return this;
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the address.
    private BlockRef nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
