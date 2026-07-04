package nl.pim16aap2.lightkeeper.agent.spigot;

import tools.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListener;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListener;

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
    RegisterEventListener.Response handleRegisterEventListener(RegisterEventListener.Command command)
    {
        final String eventClassName = requireEventClassName(command.eventClassName());
        try
        {
            eventCapture.registerListener(eventClassName);
        }
        catch (ClassNotFoundException | IllegalArgumentException exception)
        {
            throw new IllegalArgumentException(
                Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName()),
                exception
            );
        }
        return new RegisterEventListener.Response(command.requestId());
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
    GetCapturedEvents.Response handleGetCapturedEvents(GetCapturedEvents.Command command) throws Exception
    {
        final String eventClassName = requireEventClassName(command.eventClassName());
        final List<Map<String, String>> events = eventCapture.getCapturedEvents(eventClassName);
        final String eventsJson = objectMapper.writeValueAsString(events);
        return new GetCapturedEvents.Response(command.requestId(), eventsJson);
    }

    /**
     * Handles {@code CLEAR_CAPTURED_EVENTS} by discarding accumulated payloads for the event class.
     *
     * @param command
     *     Typed clear-captured-events command.
     * @return
     *     Success response.
     */
    ClearCapturedEvents.Response handleClearCapturedEvents(ClearCapturedEvents.Command command)
    {
        eventCapture.clearCapturedEvents(requireEventClassName(command.eventClassName()));
        return new ClearCapturedEvents.Response(command.requestId());
    }

    /**
     * Handles {@code UNREGISTER_EVENT_LISTENER} by removing the listener and discarding its accumulated events.
     *
     * @param command
     *     Typed unregister-event-listener command.
     * @return
     *     Success response.
     */
    UnregisterEventListener.Response handleUnregisterEventListener(UnregisterEventListener.Command command)
    {
        eventCapture.unregisterListener(requireEventClassName(command.eventClassName()));
        return new UnregisterEventListener.Response(command.requestId());
    }

    private static String requireEventClassName(String eventClassName)
    {
        if (eventClassName == null || eventClassName.isBlank())
            throw new IllegalArgumentException("eventClassName may not be blank.");
        return eventClassName;
    }
}
