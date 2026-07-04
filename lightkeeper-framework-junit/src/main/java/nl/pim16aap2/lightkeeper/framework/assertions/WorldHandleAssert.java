package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
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
        final var actual = nonNullActual();
        return new WorldBlockAssert(actual, new Vector3Di(x, y, z));
    }

    /**
     * Starts an assertion chain for a block at the requested position.
     *
     * @param position
     *     Block position.
     * @return Block assertion entrypoint.
     */
    public WorldBlockAssert hasBlockAt(Vector3Di position)
    {
        return hasBlockAt(
            position.x(),
            position.y(),
            position.z()
        );
    }

    /**
     * Asserts that the world has the expected name.
     *
     * @param worldName
     *     Expected world name.
     * @return This assertion for fluent chaining.
     */
    public WorldHandleAssert hasNameEqualTo(String worldName)
    {
        final var actual = nonNullActual();
        Assertions.assertThat(actual.name()).isEqualTo(worldName);
        return this;
    }

    /**
     * Asserts that the world has a non-blank name.
     *
     * @return This assertion for fluent chaining.
     */
    public WorldHandleAssert hasNonBlankName()
    {
        final var actual = nonNullActual();
        Assertions.assertThat(actual.name()).isNotBlank();
        return this;
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // we call isNotNull() first, so actual is not null after that
    private WorldHandle nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
