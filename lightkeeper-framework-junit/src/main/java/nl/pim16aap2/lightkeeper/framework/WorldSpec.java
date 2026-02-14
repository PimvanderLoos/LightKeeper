package nl.pim16aap2.lightkeeper.framework;

/**
 * World creation specification.
 *
 * @param name
 *     World name.
 * @param worldType
 *     World type.
 * @param environment
 *     World environment.
 * @param seed
 *     World seed.
 */
public record WorldSpec(
    String name,
    WorldType worldType,
    WorldEnvironment environment,
    long seed
)
{
    public enum WorldType
    {
        NORMAL,
        FLAT
    }

    public enum WorldEnvironment
    {
        NORMAL,
        NETHER,
        THE_END
    }
}
