package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

/**
 * Assertions for world handles.
 */
public final class WorldHandleAssert extends AbstractAssert<WorldHandleAssert, @Nullable WorldHandle>
{
    WorldHandleAssert(@Nullable WorldHandle actual)
    {
        super(actual, WorldHandleAssert.class);
    }

    /**
     * Starts an assertion chain for a block at the requested coordinates.
     *
     * @param x
     *     Block X coordinate.
     * @param y
     *     Block Y coordinate.
     * @param z
     *     Block Z coordinate.
     * @return Block assertion entrypoint.
     */
    public WorldBlockAssert hasBlockAt(int x, int y, int z)
    {
        isNotNull();
        return new WorldBlockAssert(actual, new Vector3Di(x, y, z));
    }
}
