package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
        final Vector3Di position = new Vector3Di(1, 70, 2);
        when(frameworkGateway.getBlock("world", position)).thenReturn("STONE");

        // execute
        final String blockType = worldHandle.blockTypeAt(position);

        // verify
        assertThat(blockType).isEqualTo("STONE");
        verify(frameworkGateway).getBlock("world", position);
    }

    @Test
    void setBlockAt_shouldDelegateToGateway()
    {
        // setup
        final Vector3Di position = new Vector3Di(1, 70, 2);

        // execute
        worldHandle.setBlockAt(position, "STONE");

        // verify
        verify(frameworkGateway).setBlock("world", position, "STONE");
    }

}
