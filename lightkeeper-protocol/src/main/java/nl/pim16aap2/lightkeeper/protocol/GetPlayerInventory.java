package nl.pim16aap2.lightkeeper.protocol;

import java.util.List;
import java.util.UUID;

/**
 * Returns a JSON snapshot of the player's non-air inventory slots.
 */
public final class GetPlayerInventory
{
    private GetPlayerInventory()
    {
    }

    /**
     * Command record for {@code GET_PLAYER_INVENTORY}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player whose inventory is requested.
     */
    public record Command(
        String requestId,
        UUID uuid
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(uuid, "uuid");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code GET_PLAYER_INVENTORY}.
     *
     * @param requestId
     *     Correlated request id.
     * @param items
     *     Non-air inventory slot snapshots.
     */
    public record Response(
        String requestId,
        List<ItemSnapshot> items
    ) implements IAgentResponse
    {
        /**
         * Defensively copies the item list.
         */
        public Response
        {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
