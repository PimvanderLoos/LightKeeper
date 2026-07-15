package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityQueryTest
{
    private IFrameworkGatewayView frameworkGateway;
    private EntityQuery entityQuery;

    @BeforeEach
    void setUp()
    {
        frameworkGateway = mock(IFrameworkGatewayView.class);
        entityQuery = new EntityQuery(frameworkGateway, "world");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void ofType_shouldRejectNullTypeKey()
    {
        // execute + verify
        assertThatThrownBy(() -> entityQuery.ofType(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void ofType_shouldRejectBlankTypeKey()
    {
        // execute + verify
        assertThatThrownBy(() -> entityQuery.ofType("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void ofType_shouldTrimTypeKeyBeforeUseByGateway()
    {
        // setup
        final EntityQuery typed = entityQuery.ofType("  minecraft:zombie  ");

        // execute
        typed.count();

        // verify
        verify(frameworkGateway).countEntities(
            eq("world"), eq("minecraft:zombie"), isNull(), isNull());
    }

    @Test
    void ofType_shouldReturnNewInstanceLeavingOriginalUnfiltered()
    {
        // setup
        final EntityQuery typed = entityQuery.ofType("minecraft:zombie");

        // execute
        entityQuery.count();
        typed.count();

        // verify — the original query still passes a null type filter to the gateway
        verify(frameworkGateway).countEntities(eq("world"), isNull(), isNull(), isNull());
        verify(frameworkGateway).countEntities(eq("world"), eq("minecraft:zombie"), isNull(), isNull());
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void within_shouldRejectNullMin()
    {
        // execute + verify
        assertThatThrownBy(() -> entityQuery.within(null, new BlockPos(1, 1, 1)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void within_shouldRejectNullMax()
    {
        // execute + verify
        assertThatThrownBy(() -> entityQuery.within(new BlockPos(0, 0, 0), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void within_shouldRejectBoundsWhenMinXGreaterThanMaxX()
    {
        // execute + verify
        assertThatThrownBy(() -> entityQuery.within(new BlockPos(5, 0, 0), new BlockPos(4, 0, 0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("min must be <= max");
    }

    @Test
    void within_shouldRejectBoundsWhenMinYGreaterThanMaxY()
    {
        // execute + verify
        assertThatThrownBy(() -> entityQuery.within(new BlockPos(0, 5, 0), new BlockPos(0, 4, 0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("min must be <= max");
    }

    @Test
    void within_shouldRejectBoundsWhenMinZGreaterThanMaxZ()
    {
        // execute + verify
        assertThatThrownBy(() -> entityQuery.within(new BlockPos(0, 0, 5), new BlockPos(0, 0, 4)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("min must be <= max");
    }

    @Test
    void within_shouldAllowEqualMinAndMax()
    {
        // setup
        final BlockPos pos = new BlockPos(1, 2, 3);
        final EntityQuery bounded = entityQuery.within(pos, pos);

        // execute
        bounded.count();

        // verify
        verify(frameworkGateway).countEntities(eq("world"), isNull(), eq(pos), eq(pos));
    }

    @Test
    void within_shouldReturnNewInstanceLeavingOriginalUnbounded()
    {
        // setup
        final BlockPos min = new BlockPos(0, 0, 0);
        final BlockPos max = new BlockPos(10, 10, 10);
        final EntityQuery bounded = entityQuery.within(min, max);

        // execute
        entityQuery.count();
        bounded.count();

        // verify — the original query still passes null bounds to the gateway
        verify(frameworkGateway).countEntities(eq("world"), isNull(), isNull(), isNull());
        verify(frameworkGateway).countEntities(eq("world"), isNull(), eq(min), eq(max));
    }

    @Test
    void ofTypeThenWithin_shouldChainFiltersWithoutMutatingOriginal()
    {
        // setup
        final BlockPos min = new BlockPos(0, 0, 0);
        final BlockPos max = new BlockPos(10, 10, 10);
        final EntityQuery chained = entityQuery.ofType("minecraft:zombie").within(min, max);

        // execute
        chained.count();
        entityQuery.count();

        // verify
        verify(frameworkGateway).countEntities(eq("world"), eq("minecraft:zombie"), eq(min), eq(max));
        verify(frameworkGateway).countEntities(eq("world"), isNull(), isNull(), isNull());
    }

    @Test
    void count_shouldReturnValueFromGateway()
    {
        // setup
        when(frameworkGateway.countEntities(eq("world"), isNull(), isNull(), isNull())).thenReturn(3);

        // execute
        final int result = entityQuery.count();

        // verify
        assertThat(result).isEqualTo(3);
    }

    @Test
    void snapshot_shouldDelegateToGatewayWithConfiguredFilters()
    {
        // setup
        final BlockPos min = new BlockPos(0, 0, 0);
        final BlockPos max = new BlockPos(10, 10, 10);
        final EntityQuery bounded = entityQuery.ofType("minecraft:zombie").within(min, max);
        final EntitySnapshot snapshot = new EntitySnapshot(
            java.util.UUID.randomUUID(), "minecraft:zombie", new Vec3(1, 2, 3), null, List.of(), null, 5L);
        when(frameworkGateway.snapshotEntities(eq("world"), eq("minecraft:zombie"), eq(min), eq(max)))
            .thenReturn(List.of(snapshot));

        // execute
        final List<EntitySnapshot> result = bounded.snapshot();

        // verify
        assertThat(result).containsExactly(snapshot);
    }
}
