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
     *     Unique identifier assigned to the player under {@link JoinMode#LEGACY_SPAWN}; must be {@code null}
     *     under {@link JoinMode#FULL_LOGIN}, where the server derives the offline UUID from {@code name}.
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
     * @param joinMode
     *     How the player joins the server: the full login pipeline or the internal legacy spawn.
     * @param locale
     *     Client locale (e.g. {@code "en_us"}) sent during the configuration phase under
     *     {@link JoinMode#FULL_LOGIN}, or {@code null} for the server default. Ignored under
     *     {@link JoinMode#LEGACY_SPAWN}.
     */
    public record Command(
        String requestId,
        String name,
        @Nullable UUID uuid,
        String worldName,
        @Nullable Double x,
        @Nullable Double y,
        @Nullable Double z,
        @Nullable Double health,
        @Nullable String permissionsCsv,
        JoinMode joinMode,
        @Nullable String locale
    ) implements IAgentCommand<Response>
    {
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(joinMode, "joinMode");
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("'name' must not be blank.");
            if (worldName == null || worldName.isBlank())
                throw new IllegalArgumentException("'worldName' must not be blank.");

            switch (joinMode)
            {
                case FULL_LOGIN ->
                {
                    if (uuid != null)
                        throw new IllegalArgumentException(
                            "'uuid' must be null under FULL_LOGIN: the server derives the offline UUID from the "
                                + "player name. Use LEGACY_SPAWN to supply an explicit UUID.");
                }
                case LEGACY_SPAWN -> ProtocolPreconditions.requireNonNull(uuid, "uuid");
            }

            if (locale != null && locale.isBlank())
                throw new IllegalArgumentException("'locale' must not be blank when present.");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code CREATE_PLAYER}.
     *
     * @param uuid
     *     UUID of the created synthetic player.
     * @param name
     *     Display name of the created synthetic player.
     */
    public record Response(
        UUID uuid,
        String name
    ) implements IAgentResponse
    {
    }
}
