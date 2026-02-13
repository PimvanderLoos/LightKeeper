package nl.pim16aap2.lightkeeper.framework;

import java.time.Duration;
import java.util.UUID;

/**
 * LightKeeper end-to-end test framework entrypoint.
 */
public interface ILightkeeperFramework extends AutoCloseable
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
    IPlayerBuilder buildPlayer();

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

    @Override
    void close();
}
