package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.BlockSpec;
import nl.pim16aap2.lightkeeper.framework.BlockStateSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Partial spec assertions over a frozen block state, using the snapshot's canonical matcher. */
public final class BlockStateSnapshotAssert
    extends AbstractAssert<BlockStateSnapshotAssert, @Nullable BlockStateSnapshot>
{
    BlockStateSnapshotAssert(@Nullable BlockStateSnapshot actual)
    {
        super(actual, BlockStateSnapshotAssert.class);
    }

    /**
     * Checks material and every explicitly named property.
     *
     * @param expected Partial expected block state.
     * @return This assertion.
     */
    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the snapshot.
    public BlockStateSnapshotAssert withState(BlockSpec expected)
    {
        Objects.requireNonNull(expected, "expected may not be null.");
        isNotNull();
        if (!actual.matches(expected))
            failWithMessage("Expected captured block state to match '%s', but was '%s'.",
                expected.asString(), actual.blockData());
        return this;
    }
}
