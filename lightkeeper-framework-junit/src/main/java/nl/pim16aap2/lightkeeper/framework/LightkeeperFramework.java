package nl.pim16aap2.lightkeeper.framework;

import java.time.Duration;

/**
 * LightKeeper end-to-end test framework entrypoint.
 */
public interface LightkeeperFramework extends AutoCloseable
{
    /**
     * Gets the main world handle.
     *
     * @return The main world handle.
     */
    WorldHandle mainWorld();

    /**
     * Creates a new world from a world spec.
     *
     * @param worldSpec
     *     The world specification.
     * @return The created world handle.
     */
    WorldHandle newWorld(WorldSpec worldSpec);

    /**
     * Executes a command from the requested source.
     *
     * @param source
     *     The command source.
     * @param command
     *     The command text.
     * @return Command result.
     */
    CommandResult executeCommand(CommandSource source, String command);

    /**
     * Waits until a condition is true or timeout expires.
     *
     * @param condition
     *     The condition.
     * @param timeout
     *     Timeout duration.
     */
    void waitUntil(Condition condition, Duration timeout);

    /**
     * Retrieves the block type at a position in a world.
     *
     * @param world
     *     The world handle.
     * @param position
     *     Block coordinates.
     * @return Block material name.
     */
    String blockType(WorldHandle world, Vector3Di position);

    /**
     * Sets a block type at a position in a world.
     *
     * @param world
     *     The world handle.
     * @param position
     *     Block coordinates.
     * @param material
     *     Material name (for example {@code STONE}).
     */
    void setBlock(WorldHandle world, Vector3Di position, String material);

    @Override
    void close();
}
