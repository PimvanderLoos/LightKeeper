package nl.pim16aap2.lightkeeper.framework;

/**
 * Fluent builder for worlds.
 */
public interface IWorldBuilder
{
    /**
     * Sets the world name.
     *
     * @param name
     *     World name.
     * @return This builder.
     */
    IWorldBuilder withName(String name);

    /**
     * Assigns a generated random world name.
     *
     * @return This builder.
     */
    IWorldBuilder withRandomName();

    /**
     * Sets the world type.
     *
     * @param worldType
     *     World type.
     * @return This builder.
     */
    IWorldBuilder withWorldType(WorldSpec.WorldType worldType);

    /**
     * Sets the world environment.
     *
     * @param environment
     *     World environment.
     * @return This builder.
     */
    IWorldBuilder withEnvironment(WorldSpec.WorldEnvironment environment);

    /**
     * Sets the world seed.
     *
     * @param seed
     *     World seed.
     * @return This builder.
     */
    IWorldBuilder withSeed(long seed);

    /**
     * Builds and creates the configured world.
     *
     * @return Created world handle.
     */
    WorldHandle build();
}
