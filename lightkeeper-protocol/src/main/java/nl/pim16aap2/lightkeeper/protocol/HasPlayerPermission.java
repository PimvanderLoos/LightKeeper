package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Queries the live value of a permission node for a synthetic player.
 */
public final class HasPlayerPermission
{
    private HasPlayerPermission()
    {
    }

    /**
     * Command record for {@code HAS_PLAYER_PERMISSION}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player to query.
     * @param permission
     *     The permission node to query.
     */
    public record Command(
        String requestId,
        UUID uuid,
        String permission
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(uuid, "uuid");
            ProtocolPreconditions.requireNonBlank(permission, "permission");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code HAS_PLAYER_PERMISSION}.
     *
     * @param value
     *     The live result of {@code Player#hasPermission(String)} at the time of the query.
     */
    public record Response(
        boolean value
    ) implements IAgentResponse
    {
    }
}
