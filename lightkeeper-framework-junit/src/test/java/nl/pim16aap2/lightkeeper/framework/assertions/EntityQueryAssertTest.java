package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.EntitySnapshot;
import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IFrameworkGatewayView;
import nl.pim16aap2.lightkeeper.framework.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tests cheap fresh counts, scoped diagnostics and independent snapshot bursts. */
@ExtendWith(MockitoExtension.class)
class EntityQueryAssertTest
{
    /** Gateway receiving count or snapshot reads, never chunk mutations. */
    @Mock
    private IFrameworkGatewayView gateway;

    /** Verifies that tests leave no unaccounted mock interactions. */
    @AfterEach
    void verifyInteractions()
    {
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void countAssertions_shouldRequeryWithoutFetchingEntitySnapshots()
    {
        // setup
        final var query = FrameworkHandleFactory.worldHandle(gateway, "world").entities();
        when(gateway.countEntities(eq("world"), isNull(), isNull(), isNull())).thenReturn(0, 2, 1);
        final var assertion = assertThat(query);

        // execute
        assertion
            .isEmpty()
            .hasCount(2)
            .isNotEmpty();

        // verify
        verify(gateway, times(3)).countEntities(eq("world"), isNull(), isNull(), isNull());
    }

    @Test
    void hasCount_shouldIncludeWorldTypeAndBoundsInFailure()
    {
        // setup
        final var min = new BlockPos(1, 2, 3);
        final var max = new BlockPos(4, 5, 6);
        final var query = FrameworkHandleFactory.worldHandle(gateway, "world")
            .entities()
            .ofType("minecraft:block_display")
            .within(min, max);

        when(gateway.countEntities("world", "minecraft:block_display", min, max)).thenReturn(0);

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(query).hasCount(1));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("world", "minecraft:block_display", "x=1", "x=4", "matched 0");
    }

    @Test
    void snapshot_shouldUseOneBurstWithoutExtraCountRead()
    {
        // setup
        final var snapshot = new EntitySnapshot(
            UUID.randomUUID(),
            "minecraft:armor_stand",
            new Vec3(1, 2, 3),
            null,
            List.of(),
            null,
            1
        );
        final var query = FrameworkHandleFactory.worldHandle(gateway, "world").entities();
        when(gateway.snapshotEntities(eq("world"), isNull(), isNull(), isNull())).thenReturn(List.of(snapshot));

        // execute
        final var captured = assertThat(query).snapshot();

        // verify
        captured.containsExactly(snapshot);
        verify(gateway).snapshotEntities(eq("world"), isNull(), isNull(), isNull());
    }

    @Test
    void hasCount_shouldRejectNegativeCountBeforeRead()
    {
        // setup
        final var query = FrameworkHandleFactory.worldHandle(gateway, "world").entities();

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(query).hasCount(-1));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isEmpty_shouldPropagateFailedCount()
    {
        // setup
        final var expected = new IllegalStateException("Server is unavailable.");
        final var query = FrameworkHandleFactory.worldHandle(gateway, "world").entities();
        when(gateway.countEntities(eq("world"), isNull(), isNull(), isNull())).thenThrow(expected);

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(query).isEmpty());

        // verify
        assertThat(failure).isSameAs(expected);
    }
}
