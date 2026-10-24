package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.BlockSpec;
import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IFrameworkGatewayView;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Tests direct and world-selected block assertions sharing live reads and state semantics. */
@ExtendWith(MockitoExtension.class)
class BlockRefAssertTest
{
    /** Gateway that must receive only the asserted reads. */
    @Mock
    private IFrameworkGatewayView gateway;

    /** Verifies that tests leave no unaccounted mock interactions. */
    @AfterEach
    void verifyInteractions()
    {
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void ofType_shouldReadFreshMaterialOnRetainedAddress()
    {
        // setup
        final var position = new BlockPos(1, 2, 3);
        final var block = FrameworkHandleFactory.worldHandle(gateway, "world").blockAt(position);
        when(gateway.getBlock("world", position)).thenReturn("stone", "minecraft:dirt");
        final var assertion = assertThat(block);

        // execute
        assertion
            .ofType(Material.STONE)
            .ofType("DIRT");

        // verify
        verify(gateway, times(2)).getBlock("world", position);
    }

    @Test
    void worldSelection_shouldDelegateToSameBlockRead()
    {
        // setup
        final var position = new BlockPos(1, 2, 3);
        final var world = FrameworkHandleFactory.worldHandle(gateway, "world");
        when(gateway.getBlock("world", position)).thenReturn("minecraft:stone");

        // execute
        assertThat(world)
            .hasBlockAt(position)
            .ofType("stone");

        // verify
        verify(gateway).getBlock("world", position);
    }

    @Test
    void withState_shouldUsePartialMatchingAndKeepFrozenSnapshot()
    {
        // setup
        final var position = new BlockPos(1, 2, 3);
        final var block = FrameworkHandleFactory.worldHandle(gateway, "world").blockAt(position);
        when(gateway.getBlockData("world", position)).thenReturn(
            "minecraft:lever[facing=north,powered=true]",
            "minecraft:lever[facing=south,powered=false]"
        );

        // execute
        final var captured = block.state();
        assertThat(block).withState(BlockSpec.parse("lever[powered=false]"));

        // verify
        assertThat(captured).withState(BlockSpec.parse("lever[powered=true]"));
        verify(gateway, times(2)).getBlockData("world", position);
    }

    @Test
    void withState_shouldReportWorldCoordinatesAndActualState()
    {
        // setup
        final var position = new BlockPos(1, 2, 3);
        final var block = FrameworkHandleFactory.worldHandle(gateway, "world").blockAt(position);
        when(gateway.getBlockData("world", position)).thenReturn("minecraft:lever[powered=false]");

        // execute
        final Throwable failure = catchThrowable(
            () -> assertThat(block).withState(BlockSpec.parse("lever[powered=true]"))
        );

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("world", "(1,2,3)", "powered=true", "powered=false");
        verify(gateway).getBlockData("world", position);
    }

    @Test
    void ofType_shouldRejectInvalidExpectedMaterialBeforeRead()
    {
        // setup
        final var block = FrameworkHandleFactory.worldHandle(gateway, "world").blockAt(new BlockPos(1, 2, 3));

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(block).ofType(" "));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }
}
