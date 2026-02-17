package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandleAssertionsTest
{
    @Test
    void playerHandleAssert_hasNameAndMessages()
    {
        // setup
        final PlayerHandle handle = mock(PlayerHandle.class);
        when(handle.name()).thenReturn("bot");
        when(handle.receivedMessagesText()).thenReturn("hello world");

        // execute + verify
        LightkeeperAssertions.assertThat(handle)
            .hasName("bot")
            .receivedMessage("hello")
            .receivedMessagesText()
            .contains("world");
    }

    @Test
    void playerHandleAssert_shouldFailWhenNameDoesNotMatch()
    {
        // setup
        final PlayerHandle handle = mock(PlayerHandle.class);
        when(handle.name()).thenReturn("bot");

        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.assertThat(handle).hasName("other"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Expected player name");
    }

    @Test
    void menuHandleAssert_shouldValidateTitleAndItems()
    {
        // setup
        final MenuHandle handle = mock(MenuHandle.class);
        final ItemStack itemStack = mock(ItemStack.class);
        when(handle.hasTitle("Main")).thenReturn(true);
        when(handle.hasItemAt(0, "minecraft:stone")).thenReturn(true);
        when(handle.hasItemAt(1, itemStack)).thenReturn(true);

        // execute + verify
        LightkeeperAssertions.assertThat(handle)
            .hasTitle("Main")
            .hasItemAt(0, "minecraft:stone")
            .hasItemAt(1, itemStack);
    }

    @Test
    void menuHandleAssert_shouldFailWhenItemIsMissing()
    {
        // setup
        final MenuHandle handle = mock(MenuHandle.class);
        when(handle.hasItemAt(5, "minecraft:diamond")).thenReturn(false);

        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.assertThat(handle).hasItemAt(5, "minecraft:diamond"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("slot 5");
    }

    @Test
    void worldBlockAssert_shouldValidateMaterialByEnumAndKey()
    {
        // setup
        final WorldHandle handle = mock(WorldHandle.class);
        when(handle.blockTypeAt(new Vector3Di(1, 2, 3))).thenReturn("stone");
        when(handle.blockTypeAt(new Vector3Di(4, 5, 6))).thenReturn("minecraft:dirt");

        // execute + verify
        LightkeeperAssertions.assertThat(handle).hasBlockAt(1, 2, 3).ofType(Material.STONE);
        LightkeeperAssertions.assertThat(handle).hasBlockAt(4, 5, 6).ofType("dirt");
    }

    @Test
    void worldBlockAssert_shouldFailWhenMaterialDoesNotMatch()
    {
        // setup
        final WorldHandle handle = mock(WorldHandle.class);
        when(handle.blockTypeAt(new Vector3Di(0, 0, 0))).thenReturn("minecraft:stone");

        // execute + verify
        assertThatThrownBy(() ->
            LightkeeperAssertions.assertThat(handle).hasBlockAt(0, 0, 0).ofType("minecraft:dirt"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Expected block at (0,0,0)");
    }
}
