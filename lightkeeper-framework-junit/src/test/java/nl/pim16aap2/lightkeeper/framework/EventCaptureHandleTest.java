package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventCaptureHandleTest
{
    private static final String EVENT_CLASS_NAME = "org.bukkit.event.player.PlayerDropItemEvent";

    private IFrameworkGatewayView frameworkGateway;
    private EventCaptureHandle eventCaptureHandle;

    @BeforeEach
    void setUp()
    {
        frameworkGateway = mock(IFrameworkGatewayView.class);
        eventCaptureHandle = new EventCaptureHandle(frameworkGateway, EVENT_CLASS_NAME);
    }

    @Test
    void getCapturedEvents_shouldDelegateToGateway()
    {
        // setup
        final List<CapturedEventSnapshot> events = List.of(
            new CapturedEventSnapshot(EVENT_CLASS_NAME, Map.of("isCancelled", "true"))
        );
        when(frameworkGateway.getCapturedEvents(EVENT_CLASS_NAME)).thenReturn(events);

        // execute
        final List<CapturedEventSnapshot> result = eventCaptureHandle.getCapturedEvents();

        // verify
        assertThat(result).isSameAs(events);
        verify(frameworkGateway).getCapturedEvents(EVENT_CLASS_NAME);
    }

    @Test
    void clear_shouldDelegateToGatewayAndReturnSelf()
    {
        // execute
        final EventCaptureHandle result = eventCaptureHandle.clear();

        // verify
        assertThat(result).isSameAs(eventCaptureHandle);
        verify(frameworkGateway).clearCapturedEvents(EVENT_CLASS_NAME);
    }

    @Test
    void close_shouldDelegateToGateway()
    {
        // execute
        eventCaptureHandle.close();

        // verify
        verify(frameworkGateway).unregisterEventListener(EVENT_CLASS_NAME);
    }
}
