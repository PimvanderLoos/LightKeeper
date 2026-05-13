package nl.pim16aap2.lightkeeper.protocol;

/**
 * Creates a new world on the server.
 *
 * @param requestId
 *     Correlation identifier matching the response's {@code requestId}.
 * @param worldName
 *     Unique name for the new world.
 * @param worldType
 *     World generator type; corresponds to the {@code WorldType} enum name (e.g. {@code FLAT} or {@code NORMAL}).
 * @param environment
 *     Bukkit {@code World.Environment} enum name (e.g. {@code NORMAL}, {@code NETHER}, {@code THE_END}).
 * @param seed
 *     World generation seed.
 */
public record NewWorldCommand(
    String requestId,
    String worldName,
    String worldType,
    String environment,
    long seed
) implements IAgentCommand
{
}
