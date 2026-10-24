package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IFrameworkGatewayView;
import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Tests severity separation, allowlists and non-consuming fresh error observations. */
@ExtendWith(MockitoExtension.class)
class ServerErrorsAssertTest
{
    /** Gateway from which assertions may only read structured entries. */
    @Mock
    private IFrameworkGatewayView gateway;

    /** Verifies that tests leave no unaccounted mock interactions. */
    @AfterEach
    void verifyInteractions()
    {
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void hasNoErrors_shouldIgnoreWarnings()
    {
        // setup
        when(gateway.capturedServerErrors()).thenReturn(List.of(entry(ServerErrorSnapshot.Severity.WARNING)));
        final var assertion = assertThat(FrameworkHandleFactory.serverErrorsHandle(gateway));

        // execute
        assertion.hasNoErrors();
        final Throwable failure = catchThrowable(assertion::hasNoWarnings);

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("warnings", "marker");
        verify(gateway, times(2)).capturedServerErrors();
    }

    @Test
    void hasNoWarnings_shouldLeaveErrorCheckingExplicit()
    {
        // setup
        when(gateway.capturedServerErrors()).thenReturn(List.of(entry(ServerErrorSnapshot.Severity.ERROR)));
        final var assertion = assertThat(FrameworkHandleFactory.serverErrorsHandle(gateway));

        // execute
        assertion.hasNoWarnings();
        final Throwable failure = catchThrowable(assertion::hasNoErrors);

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("errors", "marker");
    }

    @Test
    void hasNoErrors_shouldApplyAllowlistAndReadFreshState()
    {
        // setup
        when(gateway.capturedServerErrors()).thenReturn(List.of(entry(ServerErrorSnapshot.Severity.ERROR)), List.of());
        final var assertion = assertThat(FrameworkHandleFactory.serverErrorsHandle(gateway));

        // execute
        assertion.hasNoErrors(error -> error.message().equals("marker"));
        assertion.hasNoErrors();

        // verify
        verify(gateway, times(2)).capturedServerErrors();
    }

    @Test
    void captured_shouldFreezeEachNavigationWithoutClearing()
    {
        // setup
        final var warning = entry(ServerErrorSnapshot.Severity.WARNING);
        when(gateway.capturedServerErrors()).thenReturn(List.of(), List.of(warning));
        final var assertion = assertThat(FrameworkHandleFactory.serverErrorsHandle(gateway));

        // execute
        final var earlier = assertion.captured();
        final var later = assertion.captured();

        // verify
        earlier.isEmpty();
        later.containsExactly(warning);
        verify(gateway, times(2)).capturedServerErrors();
    }

    @Test
    void hasNoErrors_shouldPropagateReadFailures()
    {
        // setup
        final var expected = new IllegalStateException("Framework closed.");
        when(gateway.capturedServerErrors()).thenThrow(expected);
        final var assertion = assertThat(FrameworkHandleFactory.serverErrorsHandle(gateway));

        // execute
        final Throwable failure = catchThrowable(assertion::hasNoErrors);

        // verify
        assertThat(failure).isSameAs(expected);
    }

    private static ServerErrorSnapshot entry(ServerErrorSnapshot.Severity severity)
    {
        return new ServerErrorSnapshot(
            1,
            severity,
            severity.name(),
            "logger",
            "thread",
            "marker",
            null,
            null,
            List.of()
        );
    }
}
