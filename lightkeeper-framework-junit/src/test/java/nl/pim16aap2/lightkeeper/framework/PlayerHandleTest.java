package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.framework.internal.IFrameworkGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerHandleTest
{
    private static final UUID PLAYER_UUID = UUID.fromString("f678ad13-7dce-4b11-80d2-614dd0ff3f66");

    private IFrameworkGateway frameworkGateway;
    private PlayerHandle playerHandle;

    @BeforeEach
    void setUp()
    {
        frameworkGateway = mock(IFrameworkGateway.class);
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
    void placeBlock_shouldDelegateToGatewayAndReturnSelf()
    {
        // execute
        final PlayerHandle result = playerHandle.placeBlock("minecraft:stone", 1, 64, 2);

        // verify
        assertThat(result).isSameAs(playerHandle);
        verify(frameworkGateway).placePlayerBlock(PLAYER_UUID, "minecraft:stone", 1, 64, 2);
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
}
