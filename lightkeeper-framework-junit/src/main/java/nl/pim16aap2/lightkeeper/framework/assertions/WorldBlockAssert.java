package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.BlockSpec;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.assertj.core.api.AbstractAssert;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/** World-selected block assertions delegating to the canonical live block subject. */
public final class WorldBlockAssert extends AbstractAssert<WorldBlockAssert, @Nullable WorldHandle>
{
    /** Address selected from the world. */
    private final BlockPos position;

    WorldBlockAssert(WorldHandle actual, BlockPos position)
    {
        super(actual, WorldBlockAssert.class);
        this.position = position;
    }

    /**
     * Checks a Bukkit material using one live block read.
     *
     * @param material Expected material.
     * @return This assertion.
     */
    public WorldBlockAssert ofType(Material material)
    {
        block().ofType(material);
        return this;
    }

    /**
     * Checks a normalized material key using one live block read.
     *
     * @param materialKey Material name or namespaced key.
     * @return This assertion.
     */
    public WorldBlockAssert ofType(String materialKey)
    {
        block().ofType(materialKey);
        return this;
    }

    /**
     * Checks a partial state spec using one frozen observation.
     *
     * @param expected Partial expected state.
     * @return This assertion.
     */
    public WorldBlockAssert withState(BlockSpec expected)
    {
        block().withState(expected);
        return this;
    }

    private BlockRefAssert block()
    {
        return extracting(world -> world.blockAt(position), BlockRefAssert::new);
    }
}
