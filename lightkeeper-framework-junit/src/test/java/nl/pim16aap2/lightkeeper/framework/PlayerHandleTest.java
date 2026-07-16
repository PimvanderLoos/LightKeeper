package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.DropResult;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerHandleTest
{
    private static final UUID PLAYER_UUID = UUID.fromString("f678ad13-7dce-4b11-80d2-614dd0ff3f66");

    private IFrameworkGatewayView frameworkGateway;
    private PlayerHandle playerHandle;

    @BeforeEach
    void setUp()
    {
        frameworkGateway = mock(IFrameworkGatewayView.class);
        playerHandle = new PlayerHandle(frameworkGateway, PLAYER_UUID, "lkplayer001");
    }

    @Test
    void uniqueId_shouldReturnConfiguredValue()
    {
        // execute
        final UUID uniqueId = playerHandle.uniqueId();

        // verify
        assertThat(uniqueId).isEqualTo(PLAYER_UUID);
    }

    @Test
    void name_shouldReturnConfiguredValue()
    {
        // execute
        final String name = playerHandle.name();

        // verify
        assertThat(name).isEqualTo("lkplayer001");
    }

    @Test
    void executeCommand_shouldDelegateToGatewayAndReturnSelf()
    {
        // execute
        final PlayerHandle result = playerHandle.executeCommand("time set day");

        // verify
        assertThat(result).isSameAs(playerHandle);
        verify(frameworkGateway).executePlayerCommand(PLAYER_UUID, "time set day");
    }

    @Test
    void tabComplete_shouldDelegateToGatewayAndReturnCompletions()
    {
        // setup
        when(frameworkGateway.tabComplete(PLAYER_UUID, "/lktestg"))
            .thenReturn(List.of("/lktestgui", "/lktestlocale"));

        // execute
        final List<String> completions = playerHandle.tabComplete("/lktestg");

        // verify
        assertThat(completions).containsExactly("/lktestgui", "/lktestlocale");
        verify(frameworkGateway).tabComplete(PLAYER_UUID, "/lktestg");
    }

    @Test
    void clickChatComponent_shouldExtractRunCommandAndExecuteItAsThePlayer()
    {
        // setup — the modern 1.21.5+ wire spells the click field click_event (snake_case)
        final ChatComponentSnapshot component = new ChatComponentSnapshot(
            "{\"text\":\"Confirm\",\"click_event\":{\"action\":\"run_command\",\"command\":\"/aa confirm\"}}");

        // execute
        final PlayerHandle result = playerHandle.clickChatComponent(component);

        // verify
        assertThat(result).isSameAs(playerHandle);
        verify(frameworkGateway).executePlayerCommand(PLAYER_UUID, "/aa confirm");
    }

    @Test
    void clickChatComponent_shouldThrowWhenComponentHasNoRunCommandClick()
    {
        // setup — a suggest_command click is not a run_command click and must be rejected
        final ChatComponentSnapshot component = new ChatComponentSnapshot(
            "{\"text\":\"Type\",\"click_event\":{\"action\":\"suggest_command\",\"command\":\"/aa create \"}}");

        // execute + verify
        assertThatThrownBy(() -> playerHandle.clickChatComponent(component))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("run_command");
    }

    @Test
    void teleport_shouldDelegateToGatewayAndReturnSelf()
    {
        // setup
        final WorldHandle world = mock(WorldHandle.class);
        when(world.name()).thenReturn("world_nether");
        when(frameworkGateway.teleportPlayer(PLAYER_UUID, "world_nether", 10.5, 64.0, 20.5)).thenReturn(true);

        // execute
        final PlayerHandle result = playerHandle.teleport(world, 10.5, 64.0, 20.5);

        // verify
        assertThat(result).isSameAs(playerHandle);
        verify(frameworkGateway).teleportPlayer(PLAYER_UUID, "world_nether", 10.5, 64.0, 20.5);
    }

    @Test
    void teleport_shouldDelegateVec3OverloadToGatewayAndReturnSelf()
    {
        // setup
        final WorldHandle world = mock(WorldHandle.class);
        when(world.name()).thenReturn("world_nether");
        when(frameworkGateway.teleportPlayer(PLAYER_UUID, "world_nether", 10.5, 64.0, 20.5)).thenReturn(true);

        // execute
        final PlayerHandle result = playerHandle.teleport(world, new Vec3(10.5, 64.0, 20.5));

        // verify
        assertThat(result).isSameAs(playerHandle);
        verify(frameworkGateway).teleportPlayer(PLAYER_UUID, "world_nether", 10.5, 64.0, 20.5);
    }

    @Test
    void chat_shouldDelegateToGatewayAndReturnSelf()
    {
        // execute
        final PlayerHandle result = playerHandle.chat("hello world");

        // verify
        assertThat(result).isSameAs(playerHandle);
        verify(frameworkGateway).playerChat(PLAYER_UUID, "hello world");
    }

    @Test
    void permissions_shouldReturnNonNullPermissionControl()
    {
        // execute
        final PermissionControl permissionControl = playerHandle.permissions();

        // verify
        assertThat(permissionControl).isNotNull();
    }

    @Test
    void permissions_grantShouldDelegateToGatewayWithPlayerUuidAndReturnSameInstance()
    {
        // setup
        final PermissionControl permissionControl = playerHandle.permissions();

        // execute
        final PermissionControl result = permissionControl.grant("lightkeeper.fly");

        // verify
        assertThat(result).isSameAs(permissionControl);
        verify(frameworkGateway).grantPermission(PLAYER_UUID, "lightkeeper.fly");
    }

    @Test
    void permissions_revokeShouldDelegateToGatewayWithPlayerUuidAndReturnSameInstance()
    {
        // setup
        final PermissionControl permissionControl = playerHandle.permissions();

        // execute
        final PermissionControl result = permissionControl.revoke("lightkeeper.fly");

        // verify
        assertThat(result).isSameAs(permissionControl);
        verify(frameworkGateway).revokePermission(PLAYER_UUID, "lightkeeper.fly");
    }

    @Test
    void permissions_unsetShouldDelegateToGatewayWithPlayerUuidAndReturnSameInstance()
    {
        // setup
        final PermissionControl permissionControl = playerHandle.permissions();

        // execute
        final PermissionControl result = permissionControl.unset("lightkeeper.fly");

        // verify
        assertThat(result).isSameAs(permissionControl);
        verify(frameworkGateway).unsetPermission(PLAYER_UUID, "lightkeeper.fly");
    }

    @Test
    void permissions_hasShouldReturnGatewayValue()
    {
        // setup
        final PermissionControl permissionControl = playerHandle.permissions();
        when(frameworkGateway.hasPermission(PLAYER_UUID, "lightkeeper.fly")).thenReturn(true);

        // execute
        final boolean result = permissionControl.has("lightkeeper.fly");

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void inventory_shouldReturnInventorySnapshotFromGateway()
    {
        // setup
        final InventorySnapshot snapshot = new InventorySnapshot(List.of(
            new MenuItemSnapshot(0, "minecraft:stone", "Stone", List.of())
        ));
        when(frameworkGateway.playerInventory(PLAYER_UUID)).thenReturn(snapshot);

        // execute
        final InventorySnapshot result = playerHandle.inventory();

        // verify
        assertThat(result).isSameAs(snapshot);
        verify(frameworkGateway).playerInventory(PLAYER_UUID);
    }

    @Test
    void dropMainHandItem_shouldDelegateToGatewayAndReturnDroppedState()
    {
        // setup
        when(frameworkGateway.dropItem(PLAYER_UUID)).thenReturn(DropResult.DROPPED);

        // execute
        final DropResult result = playerHandle.dropMainHandItem();

        // verify
        assertThat(result).isEqualTo(DropResult.DROPPED);
        verify(frameworkGateway).dropItem(PLAYER_UUID);
    }

    @Test
    void teleport_shouldThrowWhenTeleportIsRejected()
    {
        // setup
        final WorldHandle world = mock(WorldHandle.class);
        when(world.name()).thenReturn("world_nether");
        when(frameworkGateway.teleportPlayer(PLAYER_UUID, "world_nether", 10.5, 64.0, 20.5)).thenReturn(false);

        // execute + verify
        assertThatThrownBy(() -> playerHandle.teleport(world, 10.5, 64.0, 20.5))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("was rejected by the server");
    }

    @Test
    void placeBlock_shouldDelegateToGatewayAndReturnSelf()
    {
        // execute
        final PlayerHandle result = playerHandle.placeBlock("minecraft:stone", 1, 64, 2);

        // verify
        assertThat(result).isSameAs(playerHandle);
        verify(frameworkGateway).placePlayerBlock(PLAYER_UUID, "minecraft:stone", 1, 64, 2);
    }

    @Test
    void leftClickBlock_shouldDelegateToGatewayAndReturnInteractionResult()
    {
        // setup
        final BlockPos position = new BlockPos(1, 64, 2);
        when(frameworkGateway.leftClickBlock(PLAYER_UUID, position, "NORTH")).thenReturn(true);

        // execute
        final InteractionResult result = playerHandle.leftClickBlock(position, BlockFace.NORTH);

        // verify
        assertThat(result).isEqualTo(new InteractionResult(true, true));
        verify(frameworkGateway).leftClickBlock(PLAYER_UUID, position, "NORTH");
    }

    @Test
    void rightClickBlock_shouldDelegateToGatewayAndReturnInteractionResult()
    {
        // setup
        final BlockPos position = new BlockPos(1, 64, 2);
        when(frameworkGateway.rightClickBlock(PLAYER_UUID, position, "SOUTH")).thenReturn(false);

        // execute
        final InteractionResult result = playerHandle.rightClickBlock(position, BlockFace.SOUTH);

        // verify
        assertThat(result).isEqualTo(new InteractionResult(true, false));
        verify(frameworkGateway).rightClickBlock(PLAYER_UUID, position, "SOUTH");
    }

    @Test
    void andWaitTicks_shouldDelegateToGatewayAndReturnSelf()
    {
        // execute
        final PlayerHandle result = playerHandle.andWaitTicks(5);

        // verify
        assertThat(result).isSameAs(playerHandle);
        verify(frameworkGateway).waitTicks(5);
    }

    @Test
    void andWaitForMenuOpen_shouldWaitWithDefaultTimeoutAndReturnHandle()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(true, "Main Menu", List.of()));
        doAnswer(invocation ->
        {
            final Condition condition = invocation.getArgument(0);
            assertThat(condition.evaluate()).isTrue();
            return null;
        }).when(frameworkGateway).waitUntil(any(Condition.class), any(Duration.class));

        // execute
        final MenuHandle menuHandle = playerHandle.andWaitForMenuOpen();

        // verify
        assertThat(menuHandle).isNotNull();
        verify(frameworkGateway).waitUntil(any(Condition.class), eq(Duration.ofSeconds(10)));
    }

    @Test
    void andWaitForMenuOpen_shouldWaitWithConfiguredTimeoutAndReturnHandle()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(true, "Main Menu", List.of()));
        doAnswer(invocation ->
        {
            final Condition condition = invocation.getArgument(0);
            assertThat(condition.evaluate()).isTrue();
            return null;
        }).when(frameworkGateway).waitUntil(any(Condition.class), any(Duration.class));

        // execute
        final MenuHandle menuHandle = playerHandle.andWaitForMenuOpen(17);

        // verify
        assertThat(menuHandle).isNotNull();
        verify(frameworkGateway).waitUntil(any(Condition.class), eq(Duration.ofSeconds(17)));
    }

    @Test
    void getMenu_shouldReturnNullWhenMenuIsClosed()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(false, "", List.of()));

        // execute
        final MenuHandle menuHandle = playerHandle.getMenu();

        // verify
        assertThat(menuHandle).isNull();
    }

    @Test
    void getMenu_shouldReturnHandleWhenMenuIsOpen()
    {
        // setup
        when(frameworkGateway.menuSnapshot(PLAYER_UUID)).thenReturn(new MenuSnapshot(true, "Main Menu", List.of()));

        // execute
        final MenuHandle menuHandle = playerHandle.getMenu();

        // verify
        assertThat(menuHandle).isNotNull();
        assertThat(Objects.requireNonNull(menuHandle).player()).isSameAs(playerHandle);
    }

    @Test
    void receivedMessages_shouldReturnMessagesFromGateway()
    {
        // setup
        when(frameworkGateway.playerMessages(PLAYER_UUID)).thenReturn(List.of("One", "Two"));

        // execute
        final List<String> messages = playerHandle.receivedMessages();

        // verify
        assertThat(messages).containsExactly("One", "Two");
    }

    @Test
    void chatComponents_shouldReturnComponentsFromGateway()
    {
        // setup
        final List<ChatComponentSnapshot> components = List.of(new ChatComponentSnapshot("{\"text\":\"Hello\"}"));
        when(frameworkGateway.playerChatComponents(PLAYER_UUID)).thenReturn(components);

        // execute
        final List<ChatComponentSnapshot> result = playerHandle.chatComponents();

        // verify
        assertThat(result).isSameAs(components);
        verify(frameworkGateway).playerChatComponents(PLAYER_UUID);
    }

    @Test
    void receivedMessagesText_shouldJoinMessagesWithLineSeparator()
    {
        // setup
        when(frameworkGateway.playerMessages(PLAYER_UUID)).thenReturn(List.of("One", "Two"));

        // execute
        final String text = playerHandle.receivedMessagesText();

        // verify
        assertThat(text).isEqualTo("One" + System.lineSeparator() + "Two");
    }

    @Test
    void remove_shouldDelegateToGateway()
    {
        // execute
        playerHandle.remove();

        // verify
        verify(frameworkGateway).removePlayer(playerHandle);
    }

    @Test
    void leftClickBlock_shouldDelegateWithDefaultBlockFaceWhenOnlyPositionGiven()
    {
        // setup
        final BlockPos position = new BlockPos(3, 70, 4);

        // execute
        final InteractionResult result = playerHandle.leftClickBlock(position);

        // verify
        assertThat(result).isEqualTo(new InteractionResult(true, false));
        verify(frameworkGateway).leftClickBlock(PLAYER_UUID, position, "UP");
    }

    @Test
    void leftClickBlock_shouldDelegateWithCoordinatesUsingDefaultBlockFace()
    {
        // execute
        final InteractionResult result = playerHandle.leftClickBlock(5, 64, 6);

        // verify
        assertThat(result).isEqualTo(new InteractionResult(true, false));
        verify(frameworkGateway).leftClickBlock(PLAYER_UUID, new BlockPos(5, 64, 6), "UP");
    }

    @Test
    void rightClickBlock_shouldDelegateWithDefaultBlockFaceWhenOnlyPositionGiven()
    {
        // setup
        final BlockPos position = new BlockPos(7, 70, 8);

        // execute
        final InteractionResult result = playerHandle.rightClickBlock(position);

        // verify
        assertThat(result).isEqualTo(new InteractionResult(true, false));
        verify(frameworkGateway).rightClickBlock(PLAYER_UUID, position, "UP");
    }

    @Test
    void rightClickBlock_shouldDelegateWithCoordinatesUsingDefaultBlockFace()
    {
        // execute
        final InteractionResult result = playerHandle.rightClickBlock(9, 64, 10);

        // verify
        assertThat(result).isEqualTo(new InteractionResult(true, false));
        verify(frameworkGateway).rightClickBlock(PLAYER_UUID, new BlockPos(9, 64, 10), "UP");
    }
}
