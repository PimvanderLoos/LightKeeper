package nl.pim16aap2.lightkeeper.framework;

import java.util.Objects;

/**
 * Handle to a world in the running Paper server.
 */
public final class WorldHandle
{
    private final LightkeeperFramework framework;
    private final String name;

    public WorldHandle(LightkeeperFramework framework, String name)
    {
        this.framework = framework;
        this.name = name;
    }

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
        return framework.blockType(this, position);
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
        framework.setBlock(this, position, material);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
            return true;
        if (!(other instanceof WorldHandle that))
            return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name);
    }
}
