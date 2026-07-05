package nl.pim16aap2.lightkeeper.protocol;

import java.util.List;
import java.util.Map;

/**
 * Returns all captured events of the given class as property-map snapshots.
 */
public final class GetCapturedEvents
{
    private GetCapturedEvents()
    {
    }

    /**
     * Command record for {@code GET_CAPTURED_EVENTS}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param eventClassName
     *     Fully-qualified class name of the Bukkit event whose captured instances are requested.
     */
    public record Command(
        String requestId,
        String eventClassName
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonBlank(eventClassName, "eventClassName");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code GET_CAPTURED_EVENTS}.
     *
     * @param events
     *     Captured event property snapshots, one map per captured event instance.
     */
    public record Response(
        List<Map<String, String>> events
    ) implements IAgentResponse
    {
        /**
         * Defensively copies the event list.
         */
        public Response
        {
            events = events == null ? List.of() : List.copyOf(events);
        }
    }
}
