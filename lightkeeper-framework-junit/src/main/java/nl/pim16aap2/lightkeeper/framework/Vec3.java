package nl.pim16aap2.lightkeeper.framework;

/**
 * Double-precision 3D position in a world.
 *
 * <p>This is the canonical vocabulary for entity positions across the framework API; the integer counterpart
 * for block coordinates is {@link BlockPos}.
 *
 * @param x
 *     X coordinate.
 * @param y
 *     Y coordinate.
 * @param z
 *     Z coordinate.
 */
public record Vec3(
    double x,
    double y,
    double z
)
{
}
