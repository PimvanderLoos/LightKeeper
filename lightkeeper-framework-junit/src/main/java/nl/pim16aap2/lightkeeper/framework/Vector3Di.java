package nl.pim16aap2.lightkeeper.framework;

/**
 * Integer 3D vector used for world block coordinates.
 *
 * @param x
 *     X coordinate.
 * @param y
 *     Y coordinate.
 * @param z
 *     Z coordinate.
 * @deprecated Use {@link BlockPos} instead; this type only remains so existing tests keep compiling for one
 *     release and will be removed together with the rest of the v1 vocabulary.
 */
@Deprecated(forRemoval = true)
public record Vector3Di(
    int x,
    int y,
    int z
)
{
    /**
     * Converts this vector to the canonical {@link BlockPos} vocabulary.
     *
     * @return The equivalent block position.
     */
    public BlockPos toBlockPos()
    {
        return new BlockPos(x, y, z);
    }
}
