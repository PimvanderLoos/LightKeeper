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
     * Sets the client locale sent during the configuration phase of a full-login join (LK-12).
     *
     * <p>Only meaningful with {@link #fullLogin()}; ignored for the default legacy spawn path.
     *
     * @param locale
     *     Locale identifier such as {@code "en_us"}; must not be blank.
     * @return This builder.
     */
    IPlayerBuilder withLocale(String locale);

    /**
     * Joins the player through the full vanilla login pipeline over real loopback TCP.
     *
     * <p>Behavioral differences from the default spawn: the real {@code AsyncPlayerPreLoginEvent},
     * {@code PlayerLoginEvent}, and {@code PlayerJoinEvent} fire; the server derives the offline UUID from the
     * name (any builder UUID is ignored); and the join can be denied (whitelist/ban/full server), surfaced as a
     * {@link BotJoinDeniedException}, or time out as a {@link BotJoinTimeoutException}.
     *
     * @return This builder.
     */
    IPlayerBuilder fullLogin();

    /**
     * Builds and joins the configured synthetic player.
     *
     * @return The created player handle.
     */
    PlayerHandle build();
}
