package nl.pim16aap2.lightkeeper.framework;

import java.util.List;
import java.util.Objects;

/**
 * Handle for a dynamic event capture session.
 */
public final class EventCaptureHandle implements AutoCloseable
{
    private final IFrameworkGatewayView frameworkGateway;
    private final String eventClassName;

    EventCaptureHandle(IFrameworkGatewayView frameworkGateway, String eventClassName)
    {
        this.frameworkGateway = Objects.requireNonNull(frameworkGateway, "frameworkGateway");
        this.eventClassName = Objects.requireNonNull(eventClassName, "eventClassName");
    }

    /**
     * Gets all captured events since the last clear or registration.
     *
     * @return List of captured event snapshots.
     */
    public List<CapturedEventSnapshot> getCapturedEvents()
    {
        return frameworkGateway.getCapturedEvents(eventClassName);
    }

    /**
     * Arms cancellation of the next {@code count} fired events of this capture's class.
     *
     * <p>The agent registers a LOWEST-priority listener, so regular plugin listeners — and this capture's
     * MONITOR listener — observe the cancelled state. Deterministic only: exactly the next {@code count}
     * events are cancelled, with no predicate filtering. The event class must implement {@code Cancellable}.
     *
     * @param count
     *     How many upcoming events to cancel; must be positive.
     * @return This handle for fluent chaining.
     */
    public EventCaptureHandle cancelNext(int count)
    {
        frameworkGateway.cancelNextEvents(eventClassName, count);
        return this;
    }

    /**
     * Clears all captured events for this session.
     *
     * @return This handle for fluent chaining.
     */
    public EventCaptureHandle clear()
    {
        frameworkGateway.clearCapturedEvents(eventClassName);
        return this;
    }

    /**
     * Stops capturing events and unregisters the listener.
     */
    @Override
    public void close()
    {
        frameworkGateway.unregisterEventListener(eventClassName);
    }
}
