package nl.pim16aap2.lightkeeper.protocol;

import java.util.List;
import java.util.Map;

/**
 * Returns all captured events of the given class as typed property snapshots.
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
     * A single captured event instance: its typed property values plus the server tick it fired on.
     *
     * @param tick
     *     The server tick at capture time.
     * @param values
     *     Accessor name to typed value, in encoding order.
     */
    public record CapturedEvent(
        long tick,
        Map<String, IProtocolValue> values
    )
    {
        /**
         * Validates and defensively copies the values, preserving their order.
         */
        public CapturedEvent
        {
            values = values == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(values));
        }
    }

    /**
     * Response record for {@code GET_CAPTURED_EVENTS}.
     *
     * @param events
     *     Captured event instances, oldest first.
     */
    public record Response(
        List<CapturedEvent> events
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
