package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
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
        return new WorldBlockAssert(actual, new BlockPos(x, y, z));
    }

    /**
     * Starts an assertion chain for a block at the requested position.
     *
     * @param position
     *     Block position.
     * @return Block assertion entrypoint.
     */
    public WorldBlockAssert hasBlockAt(BlockPos position)
    {
        return hasBlockAt(
            position.x(),
            position.y(),
            position.z()
        );
    }

    /**
     * Starts an assertion chain for a block at the requested position.
     *
     * @param position
     *     Block position.
     * @return Block assertion entrypoint.
     * @deprecated Use {@link #hasBlockAt(BlockPos)} instead.
     */
    @Deprecated(forRemoval = true)
    public WorldBlockAssert hasBlockAt(Vector3Di position)
    {
        return hasBlockAt(position.toBlockPos());
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

    /**
     * Asserts that a chunk is loaded at the target coordinates.
     *
     * @param chunkX
     *     Chunk X coordinate.
     * @param chunkZ
     *     Chunk Z coordinate.
     * @return This assertion for fluent chaining.
     */
    public WorldHandleAssert hasLoadedChunkAt(int chunkX, int chunkZ)
    {
        final var actual = nonNullActual();
        if (!actual.isChunkLoaded(chunkX, chunkZ))
        {
            failWithMessage("Expected chunk [%d, %d] in world '%s' to be loaded, but it was not.",
                chunkX, chunkZ, actual.name());
        }
        return this;
    }

    /**
     * Asserts that a chunk is not loaded at the target coordinates.
     *
     * @param chunkX
     *     Chunk X coordinate.
     * @param chunkZ
     *     Chunk Z coordinate.
     * @return This assertion for fluent chaining.
     */
    public WorldHandleAssert doesNotHaveLoadedChunkAt(int chunkX, int chunkZ)
    {
        final var actual = nonNullActual();
        if (actual.isChunkLoaded(chunkX, chunkZ))
        {
            failWithMessage("Expected chunk [%d, %d] in world '%s' to be unloaded, but it was loaded.",
                chunkX, chunkZ, actual.name());
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
