package nl.pim16aap2.lightkeeper.framework;

import lombok.EqualsAndHashCode;

import java.util.Objects;

/**
 * Handle to a world in the running test server.
 */
@EqualsAndHashCode(of = "name")
public final class WorldHandle
{
    private final IFrameworkGatewayView frameworkGateway;
    private final String name;

    WorldHandle(IFrameworkGatewayView frameworkGateway, String name)
    {
        this.frameworkGateway = Objects.requireNonNull(frameworkGateway, "frameworkGateway may not be null.");
        this.name = Objects.requireNonNull(name, "name may not be null.");
    }

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

    /**
     * Loads a chunk, generating it if it does not exist yet.
     *
     * @param chunkX
     *     Chunk X coordinate.
     * @param chunkZ
     *     Chunk Z coordinate.
     * @return This handle for fluent chaining.
     * @throws IllegalStateException
     *     If the server failed to load the chunk.
     */
    public WorldHandle loadChunk(int chunkX, int chunkZ)
    {
        if (!frameworkGateway.loadChunk(name, chunkX, chunkZ))
            throw new IllegalStateException(
                "Failed to load chunk [%d, %d] in world '%s'.".formatted(chunkX, chunkZ, name));
        return this;
    }

    /**
     * Unloads a chunk.
     *
     * @param chunkX
     *     Chunk X coordinate.
     * @param chunkZ
     *     Chunk Z coordinate.
     * @return This handle for fluent chaining.
     * @throws IllegalStateException
     *     If the server refused to unload the chunk (e.g. because it is still in use).
     */
    public WorldHandle unloadChunk(int chunkX, int chunkZ)
    {
        if (!frameworkGateway.unloadChunk(name, chunkX, chunkZ))
            throw new IllegalStateException(
                "Failed to unload chunk [%d, %d] in world '%s'; the chunk may still be in use.".formatted(
                    chunkX, chunkZ, name));
        return this;
    }

    /**
     * Checks if a chunk is loaded.
     *
     * @param chunkX
     *     Chunk X coordinate.
     * @param chunkZ
     *     Chunk Z coordinate.
     * @return True if the chunk is loaded.
     */
    public boolean isChunkLoaded(int chunkX, int chunkZ)
    {
        return frameworkGateway.isChunkLoaded(name, chunkX, chunkZ);
    }
}
