package nl.pim16aap2.lightkeeper.framework;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuHandleTest
{
    private static final UUID PLAYER_UUID = UUID.fromString("8867478f-efcb-444f-bc95-d179f50131e7");

    private IFrameworkGatewayView frameworkGateway;
    private PlayerHandle playerHandle;
    private MenuHandle menuHandle;

    @BeforeEach
    void setUp()
    {
        frameworkGateway = mock(IFrameworkGatewayView.class);
        playerHandle = new PlayerHandle(frameworkGateway, PLAYER_UUID, "lkplayer001");
        menuHandle = new MenuHandle(frameworkGateway, playerHandle);
    }

    @Test
    void player_shouldReturnOwningPlayer()
    {
        // execute
        final PlayerHandle result = menuHandle.player();

        // verify
        assertThat(result).isSameAs(playerHandle);
    }

    @Test
    void snapshot_shouldDelegateToGateway()
    {
        // setup
        final MenuSnapshot snapshot = new MenuSnapshot(true, "Main Menu", List.of());
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(snapshot);

        // execute
        final MenuSnapshot result = menuHandle.snapshot();

        // verify
        assertThat(result).isSameAs(snapshot);
    }

    @Test
    void isOpen_shouldReturnTrueWhenSnapshotIsOpen()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(true, "Main Menu", List.of()));

        // execute
        final boolean result = menuHandle.isOpen();

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void isOpen_shouldReturnFalseWhenSnapshotIsClosed()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(false, "", List.of()));

        // execute
        final boolean result = menuHandle.isOpen();

        // verify
        assertThat(result).isFalse();
    }

    @Test
    void verifyMenuName_shouldSucceedWhenMenuIsOpenAndTitleMatches()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(true, "Main Menu", List.of()));

        // execute
        final MenuHandle result = menuHandle.verifyMenuName("Main Menu");

        // verify
        assertThat(result).isSameAs(menuHandle);
    }

    @Test
    void verifyMenuName_shouldFailWhenMenuIsClosed()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(false, "Main Menu", List.of()));

        // execute + verify
        assertThatThrownBy(() -> menuHandle.verifyMenuName("Main Menu"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("menu is closed");
    }

    @Test
    void verifyMenuName_shouldFailWhenTitleDiffers()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(true, "Other Menu", List.of()));

        // execute + verify
        assertThatThrownBy(() -> menuHandle.verifyMenuName("Main Menu"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Expected menu title");
    }

    @Test
    void clickAtIndex_shouldDelegateAndReturnSelf()
    {
        // execute
        final MenuHandle result = menuHandle.clickAtIndex(4);

        // verify
        assertThat(result).isSameAs(menuHandle);
        verify(frameworkGateway).clickMenuSlot(PLAYER_UUID, 4);
    }

    @Test
    void clickAtIndex_shouldAutoWaitForOpenMenuUsingDefaultTimeout()
        throws Exception
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(true, "Main Menu", List.of()));
        final ArgumentCaptor<Condition> conditionCaptor = ArgumentCaptor.forClass(Condition.class);

        // execute
        menuHandle.clickAtIndex(4);

        // verify
        verify(frameworkGateway).waitUntil(conditionCaptor.capture(), eq(Duration.ofSeconds(10)));
        assertThat(conditionCaptor.getValue().evaluate()).isEqualTo(menuHandle.snapshot().open());
    }

    @Test
    void clickAtIndex_shouldRethrowWithPlayerNameWhenMenuNeverOpensWithinTimeout()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(false, "", List.of()));
        doThrow(new IllegalStateException("Condition did not pass within timeout PT10S."))
            .when(frameworkGateway).waitUntil(any(Condition.class), eq(Duration.ofSeconds(10)));

        // execute + verify
        assertThatThrownBy(() -> menuHandle.clickAtIndex(4))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(playerHandle.name());
    }

    @Test
    void clickItem_shouldClickFirstMatchingItemAndReturnSelf()
    {
        // setup
        final MenuItemSnapshot first = new MenuItemSnapshot(1, "minecraft:stone", "Stone Block", List.of());
        final MenuItemSnapshot second = new MenuItemSnapshot(2, "minecraft:diamond", "Shiny Diamond", List.of());
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(true, "Main Menu", List.of(first, second)));

        // execute
        final MenuHandle result = menuHandle.clickItem("Diamond");

        // verify
        assertThat(result).isSameAs(menuHandle);
        verify(frameworkGateway).clickMenuSlot(PLAYER_UUID, second.slot());
    }

    @Test
    void clickItem_shouldThrowExceptionWhenNoItemMatches()
    {
        // setup
        final MenuItemSnapshot first = new MenuItemSnapshot(1, "minecraft:stone", "Stone Block", List.of());
        final MenuItemSnapshot second = new MenuItemSnapshot(2, "minecraft:diamond", "Shiny Diamond", List.of());
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(true, "Main Menu", List.of(first, second)));

        // execute + verify
        assertThatThrownBy(() -> menuHandle.clickItem("Emerald"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Emerald")
            .hasMessageContaining("Stone Block")
            .hasMessageContaining("Shiny Diamond");
    }

    @Test
    void clickItem_shouldThrowExceptionWhenFragmentIsBlank()
    {
        // execute + verify
        assertThatThrownBy(() -> menuHandle.clickItem("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void clickItem_shouldThrowNullPointerExceptionWhenFragmentIsNull()
    {
        // execute + verify
        assertThatThrownBy(() -> menuHandle.clickItem(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void clickItem_shouldAutoWaitForOpenMenuUsingDefaultTimeout()
        throws Exception
    {
        // setup
        final MenuItemSnapshot item = new MenuItemSnapshot(1, "minecraft:stone", "Stone Block", List.of());
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(true, "Main Menu", List.of(item)));
        final ArgumentCaptor<Condition> conditionCaptor = ArgumentCaptor.forClass(Condition.class);

        // execute
        menuHandle.clickItem("Stone");

        // verify
        verify(frameworkGateway).waitUntil(conditionCaptor.capture(), eq(Duration.ofSeconds(10)));
        assertThat(conditionCaptor.getValue().evaluate()).isEqualTo(menuHandle.snapshot().open());
    }

    @Test
    void dragWithMaterial_shouldDelegateAndReturnSelf()
    {
        // execute
        final MenuHandle result = menuHandle.dragWithMaterial("minecraft:stone", 2, 3, 4);

        // verify
        assertThat(result).isSameAs(menuHandle);
        verify(frameworkGateway).dragMenuSlots(PLAYER_UUID, "minecraft:stone", 2, 3, 4);
    }

    @Test
    void dragWithMaterial_shouldAutoWaitForOpenMenuUsingDefaultTimeout()
        throws Exception
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID))
            .thenReturn(new MenuSnapshot(true, "Main Menu", List.of()));
        final ArgumentCaptor<Condition> conditionCaptor = ArgumentCaptor.forClass(Condition.class);

        // execute
        menuHandle.dragWithMaterial("minecraft:stone", 2, 3, 4);

        // verify
        verify(frameworkGateway).waitUntil(conditionCaptor.capture(), eq(Duration.ofSeconds(10)));
        assertThat(conditionCaptor.getValue().evaluate()).isEqualTo(menuHandle.snapshot().open());
    }

    @Test
    void andWaitTicks_shouldDelegateAndReturnSelf()
    {
        // execute
        final MenuHandle result = menuHandle.andWaitTicks(9);

        // verify
        assertThat(result).isSameAs(menuHandle);
        verify(frameworkGateway).waitTicks(9);
    }

    @Test
    void andWaitForMenuClose_shouldUseDefaultTimeout()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(false, "", List.of()));
        doAnswer(invocation ->
        {
            final Condition condition = invocation.getArgument(0);
            assertThat(condition.evaluate()).isTrue();
            return null;
        }).when(frameworkGateway).waitUntil(any(Condition.class), any(Duration.class));

        // execute
        final MenuHandle result = menuHandle.andWaitForMenuClose();

        // verify
        assertThat(result).isSameAs(menuHandle);
        verify(frameworkGateway).waitUntil(any(Condition.class), eq(Duration.ofSeconds(10)));
    }

    @Test
    void andWaitForMenuClose_shouldUseConfiguredTimeout()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(false, "", List.of()));
        doAnswer(invocation ->
        {
            final Condition condition = invocation.getArgument(0);
            assertThat(condition.evaluate()).isTrue();
            return null;
        }).when(frameworkGateway).waitUntil(any(Condition.class), any(Duration.class));

        // execute
        final MenuHandle result = menuHandle.andWaitForMenuClose(Duration.ofSeconds(3));

        // verify
        assertThat(result).isSameAs(menuHandle);
        verify(frameworkGateway).waitUntil(any(Condition.class), eq(Duration.ofSeconds(3)));
    }

    @Test
    void verifyMenuClosed_shouldSucceedWhenMenuIsClosed()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(false, "", List.of()));

        // execute
        final MenuHandle result = menuHandle.verifyMenuClosed();

        // verify
        assertThat(result).isSameAs(menuHandle);
    }

    @Test
    void verifyMenuClosed_shouldFailWhenMenuIsOpen()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(true, "Main Menu", List.of()));

        // execute + verify
        assertThatThrownBy(() -> menuHandle.verifyMenuClosed())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Expected menu to be closed");
    }

    @Test
    void hasTitle_shouldReturnTrueWhenTitleMatches()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(true, "Main Menu", List.of()));

        // execute
        final boolean result = menuHandle.hasTitle("Main Menu");

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void hasTitle_shouldReturnFalseWhenMenuIsClosed()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(false, "Main Menu", List.of()));

        // execute
        final boolean result = menuHandle.hasTitle("Main Menu");

        // verify
        assertThat(result).isFalse();
    }

    @Test
    void hasItemAt_shouldNormalizeMaterialKeys()
    {
        // setup
        final MenuSnapshot snapshot = new MenuSnapshot(
            true,
            "Main Menu",
            List.of(new MenuItemSnapshot(2, "minecraft:stone", "", List.of()))
        );
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(snapshot);

        // execute
        final boolean result = menuHandle.hasItemAt(2, "STONE");

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void hasItemAt_shouldReturnFalseWhenSlotDoesNotExist()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(true, "Main Menu", List.of()));

        // execute
        final boolean result = menuHandle.hasItemAt(7, "STONE");

        // verify
        assertThat(result).isFalse();
    }

    @Test
    void hasItemAtWithItemStack_shouldReturnTrueWhenStackMatchesSnapshot()
    {
        // setup
        final MenuSnapshot snapshot = new MenuSnapshot(
            true,
            "Main Menu",
            List.of(new MenuItemSnapshot(5, "minecraft:diamond_sword", "", List.of()))
        );
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(snapshot);
        final ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(itemStack.getItemMeta()).thenReturn(null);

        // execute
        final boolean result = menuHandle.hasItemAt(5, itemStack);

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void hasItemAtWithItemStack_shouldReturnFalseWhenStackDoesNotMatchSnapshot()
    {
        // setup
        final MenuSnapshot snapshot = new MenuSnapshot(
            true,
            "Main Menu",
            List.of(new MenuItemSnapshot(5, "minecraft:diamond_sword", "", List.of()))
        );
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(snapshot);
        final ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(Material.STONE);
        when(itemStack.getItemMeta()).thenReturn(null);

        // execute
        final boolean result = menuHandle.hasItemAt(5, itemStack);

        // verify
        assertThat(result).isFalse();
    }
}
