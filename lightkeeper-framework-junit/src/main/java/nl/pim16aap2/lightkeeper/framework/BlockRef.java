package nl.pim16aap2.lightkeeper.framework;

import java.util.Objects;

/**
 * LIVE address of a block in a world: it stores <em>where</em>, never <em>what</em>.
 *
 * <p>Every read re-queries the server; use {@link #state()} for a frozen {@link BlockStateSnapshot} when
 * multiple properties must be read consistently.
 */
public final class BlockRef
{
    private final IFrameworkGatewayView frameworkGateway;
    private final String worldName;
    private final BlockPos position;

    /**
     * Creates a block reference.
     *
     * @param frameworkGateway
     *     Internal gateway for operations.
     * @param worldName
     *     Name of the world containing the block.
     * @param position
     *     The block's position.
     */
    BlockRef(IFrameworkGatewayView frameworkGateway, String worldName, BlockPos position)
    {
        this.frameworkGateway = Objects.requireNonNull(frameworkGateway, "frameworkGateway may not be null.");
        this.worldName = Objects.requireNonNull(worldName, "worldName may not be null.");
        this.position = Objects.requireNonNull(position, "position may not be null.");
    }

    /**
     * Gets this reference's position.
     *
     * @return The block position.
     */
    public BlockPos pos()
    {
        return position;
    }

    /**
     * Gets the block's current material.
     *
     * @return The canonical namespaced material key.
     */
    public String material()
    {
        return MaterialKeys.normalize(frameworkGateway.getBlock(worldName, position));
    }

    /**
     * Gets a frozen snapshot of the block's full current state.
     *
     * @return The state snapshot.
     */
    public BlockStateSnapshot state()
    {
        return BlockStateSnapshot.fromBlockData(frameworkGateway.getBlockData(worldName, position));
    }

    /**
     * Checks whether the block currently matches a spec (partial matching: only the spec's named properties
     * are compared).
     *
     * @param expected
     *     The expected spec.
     * @return {@code true} when the live block matches.
     */
    public boolean is(BlockSpec expected)
    {
        return state().matches(expected);
    }
}
