package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Mutates a single permission node on a synthetic player's LightKeeper-managed permission attachment.
 */
public final class MutatePlayerPermission
{
    private MutatePlayerPermission()
    {
    }

    /**
     * The kind of mutation to apply to the permission node.
     */
    public enum Mode
    {
        /**
         * Sets the node to {@code true} on the attachment.
         */
        GRANT,

        /**
         * Sets the node to {@code false} on the attachment, overriding any default or other grant.
         */
        REVOKE,

        /**
         * Removes the node from the attachment entirely, restoring the player's default for that node.
         */
        UNSET,
    }

    /**
     * Command record for {@code MUTATE_PLAYER_PERMISSION}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player whose permission to mutate.
     * @param permission
     *     The permission node to mutate.
     * @param mode
     *     The kind of mutation to apply.
     */
    public record Command(
        String requestId,
        UUID uuid,
        String permission,
        Mode mode
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
            ProtocolPreconditions.requireNonNull(mode, "mode");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code MUTATE_PLAYER_PERMISSION}.
     */
    public record Response() implements IAgentResponse
    {
    }
}
