package nl.pim16aap2.lightkeeper.framework;

import java.util.UUID;

/**
 * Bots facet of the framework: join synthetic players into a world, or configure one through a builder.
 *
 * <p>Obtained from {@link ILightkeeperFramework#bots()}.
 */
public interface IBots
{
    /**
     * Joins a synthetic player into a world at spawn.
     *
     * @param name
     *     Player name.
     * @param world
     *     Target world.
     * @return The joined player handle.
     */
    PlayerHandle join(String name, WorldHandle world);

    /**
     * Joins a synthetic player into a world at spawn.
     *
     * @param name
     *     Player name.
     * @param uuid
     *     Player UUID.
     * @param world
     *     Target world.
     * @return The joined player handle.
     */
    PlayerHandle join(String name, UUID uuid, WorldHandle world);

    /**
     * Creates a player builder.
     *
     * @return A new player builder.
     */
    IPlayerBuilder builder();
}
