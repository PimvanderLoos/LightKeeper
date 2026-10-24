package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IFrameworkGatewayView;
import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests open/closed observations on retained menu handles. */
@ExtendWith(MockitoExtension.class)
class MenuHandleAssertTest
{
    /** Stable identity of the menu's owner. */
    private static final UUID PLAYER_ID = UUID.fromString("1479d27d-1260-48f5-8044-b6aad4c8ea0f");

    /** Gateway serving menu snapshots. */
    @Mock
    private IFrameworkGatewayView gateway;

    /** Real retained menu handle. */
    private MenuHandle menu;

    /** Verifies that tests leave no unaccounted mock interactions. */
    @AfterEach
    void verifyInteractions()
    {
        verifyNoMoreInteractions(gateway);
    }

    @BeforeEach
    void setUp()
    {
        final var player = FrameworkHandleFactory.playerHandle(gateway, PLAYER_ID, "tester");
        menu = FrameworkHandleFactory.menuHandle(gateway, player);
    }

    @Test
    void isOpen_shouldAcceptOpenMenuAndReturnSelf()
    {
        // setup
        when(gateway.menuSnapshot(PLAYER_ID)).thenReturn(new MenuSnapshot(true, "Main", List.of()));
        final var assertion = assertThat(menu);

        // execute
        final var result = assertion.isOpen();

        // verify
        assertThat(result).isSameAs(assertion);
        verify(gateway).menuSnapshot(PLAYER_ID);
    }

    @Test
    void isClosed_shouldAcceptClosedMenuAndReturnSelf()
    {
        // setup
        when(gateway.menuSnapshot(PLAYER_ID)).thenReturn(new MenuSnapshot(false, "", List.of()));
        final var assertion = assertThat(menu);

        // execute
        final var result = assertion.isClosed();

        // verify
        assertThat(result).isSameAs(assertion);
        verify(gateway).menuSnapshot(PLAYER_ID);
    }

    @Test
    void isOpen_shouldReportClosedMenuAndOwner()
    {
        // setup
        when(gateway.menuSnapshot(PLAYER_ID)).thenReturn(new MenuSnapshot(false, "", List.of()));

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(menu).isOpen());

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("tester", "to be open", "was closed");
    }

    @Test
    void isClosed_shouldReportActualOpenTitle()
    {
        // setup
        when(gateway.menuSnapshot(PLAYER_ID)).thenReturn(new MenuSnapshot(true, "Main", List.of()));

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(menu).isClosed());

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("tester", "to be closed", "was open", "Main");
        verify(gateway).menuSnapshot(PLAYER_ID);
    }

    @Test
    void isClosed_shouldReadNewStateOnRetainedSubject()
    {
        // setup
        when(gateway.menuSnapshot(PLAYER_ID)).thenReturn(
            new MenuSnapshot(true, "Main", List.of()),
            new MenuSnapshot(false, "", List.of())
        );
        final var assertion = assertThat(menu);

        // execute
        assertion
            .isOpen()
            .isClosed();

        // verify
        verify(gateway, times(2)).menuSnapshot(PLAYER_ID);
    }

    @Test
    void isClosed_shouldRejectNullHandle()
    {
        // setup
        final var assertion = new MenuHandleAssert(null);

        // execute
        final Throwable failure = catchThrowable(assertion::isClosed);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
    }

    @Test
    void isClosed_shouldPropagateFailedRead()
    {
        // setup
        final var expected = new IllegalStateException("Player is unavailable.");
        when(gateway.menuSnapshot(PLAYER_ID)).thenThrow(expected);

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(menu).isClosed());

        // verify
        assertThat(failure).isSameAs(expected);
    }
}
