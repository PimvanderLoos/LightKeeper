package nl.pim16aap2.lightkeeper.protocol;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Returns a snapshot of the inventory menu currently open for the player, or an empty response if none.
 */
public final class GetOpenMenu
{
    private GetOpenMenu()
    {
    }

    /**
     * Command record for {@code GET_OPEN_MENU}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player whose open menu is requested.
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
     * Response record for {@code GET_OPEN_MENU}.
     *
     * @param open
     *     Whether the player has an actionable inventory open.
     * @param title
     *     Title of the open inventory, or {@code null} when {@code open} is {@code false}.
     * @param items
     *     Non-air slot snapshots; empty when {@code open} is {@code false}.
     */
    public record Response(
        boolean open,
        @Nullable String title,
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
