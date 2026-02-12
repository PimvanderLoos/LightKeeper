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
 */
public record Vector3Di(
    int x,
    int y,
    int z
)
{
}
