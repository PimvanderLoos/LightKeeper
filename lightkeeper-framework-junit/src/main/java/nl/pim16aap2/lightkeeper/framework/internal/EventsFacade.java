package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.EventCaptureHandle;
import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IEvents;

import java.util.Objects;

/**
 * Default {@link IEvents} implementation.
 *
 * <p>Wraps the shared framework internals handed to it by {@link DefaultLightkeeperFramework}: it registers event
 * listeners through the agent client, deferring the shared open-state gate to the owning framework.
 */
final class EventsFacade implements IEvents
{
    private final DefaultLightkeeperFramework framework;
    private final UdsAgentClient agentClient;

    EventsFacade(DefaultLightkeeperFramework framework, UdsAgentClient agentClient)
    {
        this.framework = Objects.requireNonNull(framework, "framework may not be null.");
        this.agentClient = Objects.requireNonNull(agentClient, "agentClient may not be null.");
    }

    @Override
    public EventCaptureHandle capture(String eventClassName)
    {
        framework.ensureOpen();
        Objects.requireNonNull(eventClassName, "eventClassName may not be null.");
        agentClient.registerEventListener(eventClassName);
        return FrameworkHandleFactory.eventCaptureHandle(framework, eventClassName);
    }
}
