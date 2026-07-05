package nl.pim16aap2.lightkeeper.protocol;

import java.util.UUID;

/**
 * Simulates a click on the given inventory slot.
 */
public final class ClickMenuSlot
{
    private ClickMenuSlot()
    {
    }

    /**
     * Command record for {@code CLICK_MENU_SLOT}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param uuid
     *     Unique identifier of the player performing the click.
     * @param slot
     *     Zero-based inventory slot index to click.
     */
    public record Command(
        String requestId,
        UUID uuid,
        int slot
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonNull(uuid, "uuid");
            ProtocolPreconditions.requireNonNegative(slot, "slot");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code CLICK_MENU_SLOT}.
     *
     */
    public record Response() implements IAgentResponse
    {
    }
}
