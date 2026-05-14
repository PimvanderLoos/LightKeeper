package nl.pim16aap2.lightkeeper.protocol;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Spawns a synthetic player on the server.
 */
public final class CreatePlayer
{
    private CreatePlayer()
    {
    }

    /**
     * Command record for {@code CREATE_PLAYER}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param name
     *     Display name of the synthetic player.
     * @param uuid
     *     Unique identifier assigned to the player.
     * @param worldName
     *     Name of the world in which the player is spawned.
     * @param x
     *     Spawn X coordinate, or {@code null} to use the world spawn.
     * @param y
     *     Spawn Y coordinate, or {@code null} to use the world spawn.
     * @param z
     *     Spawn Z coordinate, or {@code null} to use the world spawn.
     * @param health
     *     Starting health value, or {@code null} to use the server default.
     * @param permissionsCsv
     *     Comma-separated list of permission nodes to grant, or {@code null} for none.
     */
    public record Command(
        String requestId,
        String name,
        UUID uuid,
        String worldName,
        @Nullable Double x,
        @Nullable Double y,
        @Nullable Double z,
        @Nullable Double health,
        @Nullable String permissionsCsv
    ) implements IAgentCommand<Response>
    {
        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code CREATE_PLAYER}.
     *
     * @param requestId
     *     Correlated request id.
     * @param uuid
     *     UUID of the created synthetic player.
     * @param name
     *     Display name of the created synthetic player.
     */
    public record Response(
        String requestId,
        UUID uuid,
        String name
    ) implements IAgentResponse
    {
    }
}
