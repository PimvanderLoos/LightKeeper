package nl.pim16aap2.lightkeeper.framework;

import java.time.Duration;
import java.util.UUID;

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
     * Creates a new world using framework defaults.
     *
     * @return The created world handle.
     */
    WorldHandle newWorld();

    /**
     * Creates a new world from a world spec.
     *
     * @param worldSpec
     *     The world specification.
     * @return The created world handle.
     */
    WorldHandle newWorld(WorldSpec worldSpec);

    /**
     * Creates a synthetic player in a world at spawn.
     *
     * @param name
     *     Player name.
     * @param world
     *     Target world.
     * @return Created player handle.
     */
    PlayerHandle createPlayer(String name, WorldHandle world);

    /**
     * Creates a synthetic player in a world at spawn.
     *
     * @param name
     *     Player name.
     * @param uuid
     *     Player UUID.
     * @param world
     *     Target world.
     * @return Created player handle.
     */
    PlayerHandle createPlayer(String name, UUID uuid, WorldHandle world);

    /**
     * Creates a player builder.
     *
     * @return A new player builder.
     */
    PlayerBuilder buildPlayer();

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

    /**
     * Executes a command as a synthetic player.
     *
     * @param player
     *     Player handle.
     * @param command
     *     Command text.
     */
    void executePlayerCommand(PlayerHandle player, String command);

    /**
     * Places a block as a synthetic player.
     *
     * @param player
     *     Player handle.
     * @param material
     *     Material key.
     * @param x
     *     X coordinate.
     * @param y
     *     Y coordinate.
     * @param z
     *     Z coordinate.
     */
    void placePlayerBlock(PlayerHandle player, String material, int x, int y, int z);

    /**
     * Gets the current menu snapshot for a player.
     *
     * @param player
     *     Player handle.
     * @return Menu snapshot.
     */
    MenuSnapshot menuSnapshot(PlayerHandle player);

    /**
     * Clicks a raw slot in the player menu.
     *
     * @param player
     *     Player handle.
     * @param slot
     *     Raw slot index.
     */
    void clickMenuSlot(PlayerHandle player, int slot);

    /**
     * Drags an item over raw slots in the player menu.
     *
     * @param player
     *     Player handle.
     * @param materialKey
     *     Material key.
     * @param slots
     *     Raw slot indices.
     */
    void dragMenuSlots(PlayerHandle player, String materialKey, int... slots);

    /**
     * Removes a synthetic player.
     *
     * @param player
     *     Player handle.
     */
    void removePlayer(PlayerHandle player);

    /**
     * Waits until at least the requested amount of server ticks have passed.
     *
     * @param ticks
     *     Tick count to wait.
     */
    void waitTicks(int ticks);

    @Override
    void close();
}
