package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Teleports a synthetic player to the given location.
 */
public final class TeleportPlayer
{
    private TeleportPlayer()
    {
    }

    /**
     * Command record for {@code TELEPORT_PLAYER}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player to teleport.
     * @param worldName
     *     Name of the destination world.
     * @param x
     *     Destination X coordinate.
     * @param y
     *     Destination Y coordinate.
     * @param z
     *     Destination Z coordinate.
     */
    public record Command(
        String requestId,
        UUID uuid,
        String worldName,
        double x,
        double y,
        double z
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(uuid, "uuid");
            ProtocolPreconditions.requireNonBlank(worldName, "worldName");
            ProtocolPreconditions.requireFinite(x, "x");
            ProtocolPreconditions.requireFinite(y, "y");
            ProtocolPreconditions.requireFinite(z, "z");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code TELEPORT_PLAYER}.
     *
     * @param teleported
     *     Whether the teleportation succeeded.
     */
    public record Response(
        boolean teleported
    ) implements IAgentResponse
    {
    }
}
