package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.CancelNextEvents;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListener;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListener;

import java.util.List;
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
     * @param eventCapture
     *     Dynamic event capture facade.
     */
    AgentEventActions(AgentEventCapture eventCapture)
    {
        this.eventCapture = Objects.requireNonNull(eventCapture, "eventCapture");
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
        final String eventClassName = command.eventClassName();
        try
        {
            eventCapture.registerListener(eventClassName);
        }
        catch (ClassNotFoundException exception)
        {
            throw new IllegalArgumentException("Event class not found: " + eventClassName, exception);
        }
        catch (IllegalArgumentException exception)
        {
            throw new IllegalArgumentException(
                Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName()),
                exception
            );
        }
        return new RegisterEventListener.Response();
    }

    /**
     * Handles {@code GET_CAPTURED_EVENTS} by returning all events captured since the listener was registered or
     * last cleared.
     *
     * @param command
     *     Typed get-captured-events command.
     * @return
     *     Success response with the captured events, or {@code INVALID_ARGUMENT} when the class name is blank.
     */
    GetCapturedEvents.Response handleGetCapturedEvents(GetCapturedEvents.Command command)
    {
        final String eventClassName = command.eventClassName();
        final List<GetCapturedEvents.CapturedEvent> events = eventCapture.getCapturedEvents(eventClassName);
        return new GetCapturedEvents.Response(events);
    }

    /**
     * Handles {@code CANCEL_NEXT_EVENTS} by arming a LOWEST-priority listener that cancels the next N fired
     * events of the class.
     *
     * @param command
     *     Typed cancel-next-events command.
     * @return
     *     Success response, or {@code INVALID_ARGUMENT} when the class is unknown or not Cancellable.
     */
    CancelNextEvents.Response handleCancelNextEvents(CancelNextEvents.Command command)
    {
        try
        {
            eventCapture.cancelNextEvents(command.eventClassName(), command.count());
        }
        catch (ClassNotFoundException exception)
        {
            throw new IllegalArgumentException("Event class not found: " + command.eventClassName(), exception);
        }
        catch (IllegalStateException exception)
        {
            // Re-arming while a cancellation is active is caller error, not a server failure: surface it as
            // INVALID_ARGUMENT instead of REQUEST_FAILED + a SEVERE log the error capture would pick up.
            throw new IllegalArgumentException(
                Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName()),
                exception);
        }
        return new CancelNextEvents.Response();
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
        eventCapture.clearCapturedEvents(command.eventClassName());
        return new ClearCapturedEvents.Response();
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
        eventCapture.unregisterListener(command.eventClassName());
        return new UnregisterEventListener.Response();
    }
}
