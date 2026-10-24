package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IFrameworkGatewayView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Tests fresh, non-consuming capture observations and retry behavior. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class EventCaptureAssertTest
{
    /** Gateway from which assertions may only read captured events. */
    @Mock
    private IFrameworkGatewayView gateway;

    @Test
    void hasEventCount_shouldReadFreshBufferAndWorkWithEventually()
    {
        // setup
        final var event = new CapturedEventSnapshot("Event", 1, Map.of());
        when(gateway.getCapturedEvents("Event")).thenReturn(List.of(), List.of(event));
        final var assertion = assertThat(FrameworkHandleFactory.eventCaptureHandle(gateway, "Event"));

        // execute
        eventually(Duration.ofSeconds(2), () -> assertion.hasEventCount(1));

        // verify
        verify(gateway, times(2)).getCapturedEvents("Event");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void events_shouldCaptureFrozenListWithoutClearingBuffer()
    {
        // setup
        final var event = new CapturedEventSnapshot("Event", 1, Map.of());
        when(gateway.getCapturedEvents("Event")).thenReturn(List.of(), List.of(event));
        final var assertion = assertThat(FrameworkHandleFactory.eventCaptureHandle(gateway, "Event"));

        // execute
        final var earlier = assertion.events();
        final var later = assertion.events();

        // verify
        earlier.isEmpty();
        later.containsExactly(event);
        verify(gateway, times(2)).getCapturedEvents("Event");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void hasNoEvents_shouldIncludeEventIdentityAndCountOnFailure()
    {
        // setup
        when(gateway.getCapturedEvents("Event")).thenReturn(List.of(new CapturedEventSnapshot("Event", 1, Map.of())));
        final var assertion = assertThat(FrameworkHandleFactory.eventCaptureHandle(gateway, "Event"));

        // execute
        final Throwable failure = catchThrowable(assertion::hasNoEvents);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContainingAll("Event", "contained 1");
    }

    @Test
    void hasEventCount_shouldRejectNegativeCountBeforeReading()
    {
        // setup
        final var assertion = assertThat(FrameworkHandleFactory.eventCaptureHandle(gateway, "Event"));

        // execute
        final Throwable failure = catchThrowable(() -> assertion.hasEventCount(-1));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void hasNoEvents_shouldPropagateFailedReads()
    {
        // setup
        final var expected = new IllegalStateException("Capture is closed.");
        when(gateway.getCapturedEvents("Event")).thenThrow(expected);
        final var assertion = assertThat(FrameworkHandleFactory.eventCaptureHandle(gateway, "Event"));

        // execute
        final Throwable failure = catchThrowable(assertion::hasNoEvents);

        // verify
        assertThat(failure).isSameAs(expected);
    }
}
