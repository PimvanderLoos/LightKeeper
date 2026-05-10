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
