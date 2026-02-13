package nl.pim16aap2.lightkeeper.framework;

/**
 * Fluent builder for synthetic players.
 */
public interface IPlayerBuilder
{
    /**
     * Sets the player name.
     *
     * @param name
     *     Name to assign.
     * @return This builder.
     */
    IPlayerBuilder withName(String name);

    /**
     * Assigns a generated random name (and associated UUID seed).
     *
     * @return This builder.
     */
    IPlayerBuilder withRandomName();

    /**
     * Places the player in the target world at spawn coordinates.
     *
     * @param world
     *     Target world.
     * @return This builder.
     */
    IPlayerBuilder inWorld(WorldHandle world);

    /**
     * Places the player in the target world at explicit coordinates.
     *
     * @param world
     *     Target world.
     * @param x
     *     X coordinate.
     * @param y
     *     Y coordinate.
     * @param z
     *     Z coordinate.
     * @return This builder.
     */
    IPlayerBuilder atLocation(WorldHandle world, double x, double y, double z);

    /**
     * Places the player at the world spawn location.
     *
     * @param world
     *     Target world.
     * @return This builder.
     */
    IPlayerBuilder atSpawn(WorldHandle world);

    /**
     * Sets the synthetic player health value.
     *
     * @param health
     *     Health to assign.
     * @return This builder.
     */
    IPlayerBuilder withHealth(double health);

    /**
     * Grants permissions to the synthetic player.
     *
     * @param permissions
     *     Permission nodes.
     * @return This builder.
     */
    IPlayerBuilder withPermissions(String... permissions);

    /**
     * Builds and spawns the configured synthetic player.
     *
     * @return The created player handle.
     */
    PlayerHandle build();
}
