package nl.pim16aap2.lightkeeper.framework;

/**
 * Integer block coordinates in a world.
 *
 * <p>This is the canonical vocabulary for block positions across the framework API; the double-precision
 * counterpart for entity positions is {@link Vec3}.
 *
 * @param x
 *     X coordinate.
 * @param y
 *     Y coordinate.
 * @param z
 *     Z coordinate.
 */
public record BlockPos(
    int x,
    int y,
    int z
)
{
}
