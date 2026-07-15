package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldHandleTest
{
    private IFrameworkGatewayView frameworkGateway;
    private WorldHandle worldHandle;

    @BeforeEach
    void setUp()
    {
        frameworkGateway = mock(IFrameworkGatewayView.class);
        worldHandle = new WorldHandle(frameworkGateway, "world");
    }

    @Test
    void name_shouldReturnConfiguredName()
    {
        // execute
        final String name = worldHandle.name();

        // verify
        assertThat(name).isEqualTo("world");
    }

    @Test
    void blockTypeAt_shouldDelegateToGateway()
    {
        // setup
        final BlockPos position = new BlockPos(1, 70, 2);
        when(frameworkGateway.getBlock("world", position)).thenReturn("STONE");

        // execute
        final String blockType = worldHandle.blockTypeAt(position);

        // verify
        assertThat(blockType).isEqualTo("STONE");
        verify(frameworkGateway).getBlock("world", position);
    }

    @Test
    @SuppressWarnings("removal")
    void blockTypeAt_shouldDelegateFromDeprecatedVector3DiOverload()
    {
        // setup
        final Vector3Di position = new Vector3Di(1, 70, 2);
        when(frameworkGateway.getBlock("world", position.toBlockPos())).thenReturn("STONE");

        // execute
        final String blockType = worldHandle.blockTypeAt(position);

        // verify
        assertThat(blockType).isEqualTo("STONE");
        verify(frameworkGateway).getBlock("world", position.toBlockPos());
    }

    @Test
    void setBlockAt_shouldDelegateToGateway()
    {
        // setup
        final BlockPos position = new BlockPos(1, 70, 2);

        // execute
        worldHandle.setBlockAt(position, "STONE");

        // verify
        verify(frameworkGateway).setBlock("world", position, "STONE");
    }

    @Test
    @SuppressWarnings("removal")
    void setBlockAt_shouldDelegateFromDeprecatedVector3DiOverload()
    {
        // setup
        final Vector3Di position = new Vector3Di(1, 70, 2);

        // execute
        worldHandle.setBlockAt(position, "STONE");

        // verify
        verify(frameworkGateway).setBlock("world", position.toBlockPos(), "STONE");
    }

    @Test
    void loadChunk_shouldDelegateToGatewayAndReturnSelf()
    {
        // setup
        when(frameworkGateway.loadChunk("world", 1, 2)).thenReturn(true);

        // execute
        final WorldHandle result = worldHandle.loadChunk(1, 2);

        // verify
        assertThat(result).isSameAs(worldHandle);
        verify(frameworkGateway).loadChunk("world", 1, 2);
    }

    @Test
    void loadChunk_shouldThrowWhenLoadFails()
    {
        // setup
        when(frameworkGateway.loadChunk("world", 1, 2)).thenReturn(false);

        // execute + verify
        assertThatThrownBy(() -> worldHandle.loadChunk(1, 2))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load chunk [1, 2] in world 'world'");
    }

    @Test
    void unloadChunk_shouldDelegateToGatewayAndReturnSelf()
    {
        // setup
        when(frameworkGateway.unloadChunk("world", 1, 2)).thenReturn(true);

        // execute
        final WorldHandle result = worldHandle.unloadChunk(1, 2);

        // verify
        assertThat(result).isSameAs(worldHandle);
        verify(frameworkGateway).unloadChunk("world", 1, 2);
    }

    @Test
    void unloadChunk_shouldThrowWhenUnloadFails()
    {
        // setup
        when(frameworkGateway.unloadChunk("world", 1, 2)).thenReturn(false);

        // execute + verify
        assertThatThrownBy(() -> worldHandle.unloadChunk(1, 2))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to unload chunk [1, 2] in world 'world'");
    }

    @Test
    void isChunkLoaded_shouldDelegateToGateway()
    {
        // setup
        when(frameworkGateway.isChunkLoaded("world", 1, 2)).thenReturn(true);

        // execute
        final boolean result = worldHandle.isChunkLoaded(1, 2);

        // verify
        assertThat(result).isTrue();
        verify(frameworkGateway).isChunkLoaded("world", 1, 2);
    }

    @Test
    void blockAt_shouldReturnRefWiredToGateway()
    {
        // setup
        final BlockPos position = new BlockPos(1, 70, 2);
        when(frameworkGateway.getBlock("world", position)).thenReturn("STONE");

        // execute
        final BlockRef ref = worldHandle.blockAt(position);

        // verify
        assertThat(ref.pos()).isEqualTo(position);
        assertThat(ref.material()).isEqualTo("minecraft:stone");
        verify(frameworkGateway).getBlock("world", position);
    }

    @Test
    void setBlockAt_shouldDelegateToGatewaySetBlockDataWithSpecString()
    {
        // setup
        final BlockPos position = new BlockPos(1, 70, 2);
        final BlockSpec spec = BlockSpec.parse("minecraft:lever[powered=true]");

        // execute
        worldHandle.setBlockAt(position, spec);

        // verify
        verify(frameworkGateway).setBlockData("world", position, "minecraft:lever[powered=true]");
    }

}
