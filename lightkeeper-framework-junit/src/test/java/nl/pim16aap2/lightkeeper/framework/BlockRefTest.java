package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockRefTest
{
    private IFrameworkGatewayView frameworkGateway;
    private BlockPos position;
    private BlockRef blockRef;

    @BeforeEach
    void setUp()
    {
        frameworkGateway = mock(IFrameworkGatewayView.class);
        position = new BlockPos(1, 70, 2);
        blockRef = new BlockRef(frameworkGateway, "world", position);
    }

    @Test
    void pos_shouldReturnConfiguredPosition()
    {
        // execute
        final BlockPos result = blockRef.pos();

        // verify
        assertThat(result).isEqualTo(position);
    }

    @Test
    void material_shouldReturnNormalizedMaterialFromGateway()
    {
        // setup
        when(frameworkGateway.getBlock("world", position)).thenReturn("STONE");

        // execute
        final String material = blockRef.material();

        // verify
        assertThat(material).isEqualTo("minecraft:stone");
        verify(frameworkGateway).getBlock("world", position);
    }

    @Test
    void state_shouldReturnSnapshotParsedFromGatewayBlockData()
    {
        // setup
        when(frameworkGateway.getBlockData("world", position))
            .thenReturn("minecraft:lever[face=floor,facing=north,powered=true]");

        // execute
        final BlockStateSnapshot snapshot = blockRef.state();

        // verify
        assertThat(snapshot.materialKey()).isEqualTo("minecraft:lever");
        assertThat(snapshot.properties()).containsEntry("powered", "true");
        verify(frameworkGateway).getBlockData("world", position);
    }

    @Test
    void is_shouldReturnTrueWhenLiveStateMatchesSpec()
    {
        // setup
        when(frameworkGateway.getBlockData("world", position))
            .thenReturn("minecraft:lever[face=floor,facing=north,powered=true]");

        // execute
        final boolean result = blockRef.is(BlockSpec.parse("minecraft:lever[powered=true]"));

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void is_shouldReturnFalseWhenLiveStateDoesNotMatchSpec()
    {
        // setup
        when(frameworkGateway.getBlockData("world", position))
            .thenReturn("minecraft:lever[face=floor,facing=north,powered=false]");

        // execute
        final boolean result = blockRef.is(BlockSpec.parse("minecraft:lever[powered=true]"));

        // verify
        assertThat(result).isFalse();
    }
}
