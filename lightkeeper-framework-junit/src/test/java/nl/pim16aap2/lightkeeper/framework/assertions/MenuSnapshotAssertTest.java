package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IFrameworkGatewayView;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Tests shared menu rules, snapshot isolation and non-mutating live navigation. */
@ExtendWith(MockitoExtension.class)
class MenuSnapshotAssertTest
{
    /** Gateway supplying menu observations. */
    @Mock
    private IFrameworkGatewayView gateway;

    /** Verifies that tests leave no unaccounted mock interactions. */
    @AfterEach
    void verifyInteractions()
    {
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void hasEmptySlot_shouldRequireOpenMenu()
    {
        // setup
        final var assertion = assertThat(new MenuSnapshot(false, "", List.of()));

        // execute
        final Throwable failure = catchThrowable(() -> assertion.hasEmptySlot(0));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("closed");
        assertion.isClosed();
    }

    @Test
    void hasTitle_shouldReportActualTitle()
    {
        // setup
        final var assertion = assertThat(new MenuSnapshot(true, "Actual", List.of()));

        // execute
        final Throwable failure = catchThrowable(() -> assertion.hasTitle("Expected"));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("Expected", "Actual");
    }

    @Test
    void snapshot_shouldFreezeWhileRetainedMenuContinuesReading()
    {
        // setup
        final UUID id = UUID.randomUUID();
        final var player = FrameworkHandleFactory.playerHandle(gateway, id, "tester");
        final var menu = FrameworkHandleFactory.menuHandle(gateway, player);
        when(gateway.menuSnapshot(id)).thenReturn(
            new MenuSnapshot(true, "Main", List.of()),
            new MenuSnapshot(false, "", List.of())
        );
        final var live = assertThat(menu);

        // execute
        final var frozen = live.snapshot();
        live.isClosed();

        // verify
        frozen
            .isOpen()
            .hasTitle("Main")
            .isEmpty();
        verify(gateway, times(2)).menuSnapshot(id);
    }

    @Test
    void constructor_shouldDefensivelyCopyMenuItems()
    {
        // setup
        final var items = new ArrayList<>(List.of(new MenuItemSnapshot(0, "stone", "", List.of())));
        final var menu = new MenuSnapshot(true, "Main", items);

        // execute
        items.clear();

        // verify
        assertThat(menu)
            .hasOccupiedSlotCount(1)
            .hasItemAt(0, "STONE");
    }

    @Test
    void itemAt_shouldPreserveButNotShareAssertionDescription()
    {
        // setup
        final var assertion = assertThat(
            new MenuSnapshot(true, "Main", List.of(new MenuItemSnapshot(0, "stone", "", List.of())))
        ).as("Parent menu");
        final var item = assertion
            .itemAt(0)
            .as("Child item");

        // execute
        final Throwable menuFailure = catchThrowable(() -> assertion.hasTitle("Other"));
        final Throwable itemFailure = catchThrowable(() -> item.isMaterial("dirt"));

        // verify
        assertThat(menuFailure)
            .hasMessageContaining("Parent menu")
            .hasMessageNotContaining("Child item");
        assertThat(itemFailure)
            .hasMessageContaining("Child item");
    }

    @Test
    void isClosed_shouldRejectNullSnapshot()
    {
        // setup
        final var assertion = new MenuSnapshotAssert(null);

        // execute
        final Throwable failure = catchThrowable(assertion::isClosed);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
    }
}
