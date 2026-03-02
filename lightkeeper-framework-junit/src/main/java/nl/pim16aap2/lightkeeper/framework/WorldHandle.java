package nl.pim16aap2.lightkeeper.framework;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import nl.pim16aap2.lightkeeper.framework.internal.IFrameworkGateway;

import java.util.Objects;

/**
 * Handle to a world in the running test server.
 */
@RequiredArgsConstructor
@EqualsAndHashCode(of = "name")
public final class WorldHandle
{
    private final IFrameworkGateway frameworkGateway;
    private final String name;

    /**
     * Gets the world name.
     *
     * @return World name.
     */
    public String name()
    {
        return name;
    }

    /**
     * Retrieves the block type at a position.
     *
     * @param position
     *     Block coordinates.
     * @return The block material name.
     */
    public String blockTypeAt(Vector3Di position)
    {
        Objects.requireNonNull(position, "position may not be null.");
        return frameworkGateway.getBlock(name, position);
    }

    /**
     * Sets the block type at a position.
     *
     * @param position
     *     Block coordinates.
     * @param material
     *     Material name.
     */
    public void setBlockAt(Vector3Di position, String material)
    {
        Objects.requireNonNull(position, "position may not be null.");
        frameworkGateway.setBlock(name, position, material);
    }
}
