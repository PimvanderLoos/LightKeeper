package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEventsCommand;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEventsCommand;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListenerCommand;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListenerCommand;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Protocol action handler for dynamic Bukkit event capture.
 *
 * <p>Handles {@code REGISTER_EVENT_LISTENER}, {@code GET_CAPTURED_EVENTS},
 * {@code CLEAR_CAPTURED_EVENTS}, and {@code UNREGISTER_EVENT_LISTENER} actions by delegating to
 * {@link AgentEventCapture}.
 */
final class AgentEventActions
{
    /**
     * Dynamic event listener and payload store.
     */
    private final AgentEventCapture eventCapture;
    /**
     * JSON serializer for the captured event payload list.
     */
    private final ObjectMapper objectMapper;

    /**
     * @param eventCapture
     *     Dynamic event capture facade.
     * @param objectMapper
     *     JSON serializer for event data.
     */
    AgentEventActions(AgentEventCapture eventCapture, ObjectMapper objectMapper)
    {
        this.eventCapture = Objects.requireNonNull(eventCapture, "eventCapture");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Handles {@code REGISTER_EVENT_LISTENER} by resolving and registering a monitor-priority Bukkit listener.
     *
     * @param command
     *     Typed register-event-listener command.
     * @return
     *     Success response, or {@code INVALID_ARGUMENT} when the class is blank, unknown, or not a Bukkit Event.
     */
    AgentResponse handleRegisterEventListener(RegisterEventListenerCommand command)
    {
        final String eventClassName = command.eventClassName();
        if (eventClassName == null || eventClassName.isBlank())
        {
            return AgentResponses.errorResponse(
                command.requestId(), AgentErrorCode.INVALID_ARGUMENT, "eventClassName may not be blank."
            );
        }
        try
        {
            eventCapture.registerListener(eventClassName);
        }
        catch (ClassNotFoundException | IllegalArgumentException exception)
        {
            return AgentResponses.errorResponse(
                command.requestId(),
                AgentErrorCode.INVALID_ARGUMENT,
                Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName())
            );
        }
        catch (Exception exception)
        {
            return AgentResponses.errorResponse(
                command.requestId(),
                AgentErrorCode.REQUEST_FAILED,
                Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName())
            );
        }
        return AgentResponses.successResponse(command.requestId(), Map.of());
    }

    /**
     * Handles {@code GET_CAPTURED_EVENTS} by returning all events captured since the listener was registered or
     * last cleared.
     *
     * @param command
     *     Typed get-captured-events command.
     * @return
     *     Success response with {@code eventsJson}, or {@code INVALID_ARGUMENT} when the class name is blank.
     * @throws Exception
     *     Propagates JSON serialization failures.
     */
    AgentResponse handleGetCapturedEvents(GetCapturedEventsCommand command) throws Exception
    {
        final String eventClassName = command.eventClassName();
        if (eventClassName == null || eventClassName.isBlank())
        {
            return AgentResponses.errorResponse(
                command.requestId(), AgentErrorCode.INVALID_ARGUMENT, "eventClassName may not be blank."
            );
        }
        final List<Map<String, String>> events = eventCapture.getCapturedEvents(eventClassName);
        final String eventsJson = objectMapper.writeValueAsString(events);
        return AgentResponses.successResponse(command.requestId(), Map.of("eventsJson", eventsJson));
    }

    /**
     * Handles {@code CLEAR_CAPTURED_EVENTS} by discarding accumulated payloads for the event class.
     *
     * @param command
     *     Typed clear-captured-events command.
     * @return
     *     Success response.
     */
    AgentResponse handleClearCapturedEvents(ClearCapturedEventsCommand command)
    {
        final String eventClassName = command.eventClassName();
        if (eventClassName != null && !eventClassName.isBlank())
            eventCapture.clearCapturedEvents(eventClassName);
        return AgentResponses.successResponse(command.requestId(), Map.of());
    }

    /**
     * Handles {@code UNREGISTER_EVENT_LISTENER} by removing the listener and discarding its accumulated events.
     *
     * @param command
     *     Typed unregister-event-listener command.
     * @return
     *     Success response.
     */
    AgentResponse handleUnregisterEventListener(UnregisterEventListenerCommand command)
    {
        final String eventClassName = command.eventClassName();
        if (eventClassName != null && !eventClassName.isBlank())
            eventCapture.unregisterListener(eventClassName);
        return AgentResponses.successResponse(command.requestId(), Map.of());
    }
}
