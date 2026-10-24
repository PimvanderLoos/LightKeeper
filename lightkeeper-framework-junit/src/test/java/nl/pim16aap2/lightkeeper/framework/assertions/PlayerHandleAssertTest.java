package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import nl.pim16aap2.lightkeeper.framework.IFrameworkGatewayView;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Tests permissions and menu availability through real handles and a strict mocked gateway. */
@ExtendWith(MockitoExtension.class)
class PlayerHandleAssertTest
{
    /** Stable identity for gateway expectations. */
    private static final UUID PLAYER_ID = UUID.fromString("1479d27d-1260-48f5-8044-b6aad4c8ea0f");

    /** Gateway receiving only read operations from assertions. */
    @Mock
    private IFrameworkGatewayView gateway;

    /** Real player handle whose permission and menu reads use the gateway. */
    private PlayerHandle player;

    /** Verifies that tests leave no unaccounted mock interactions. */
    @AfterEach
    void verifyInteractions()
    {
        verifyNoMoreInteractions(gateway);
    }

    @BeforeEach
    void setUp()
    {
        player = FrameworkHandleFactory.playerHandle(gateway, PLAYER_ID, "tester");
    }

    @Test
    void hasPermission_shouldReadEffectivePermissionAndReturnSelf()
    {
        // setup
        when(gateway.hasPermission(PLAYER_ID, "example.use")).thenReturn(true);
        final var assertion = assertThat(player);

        // execute
        final var result = assertion.hasPermission("example.use");

        // verify
        assertThat(result).isSameAs(assertion);
        verify(gateway).hasPermission(PLAYER_ID, "example.use");
    }

    @Test
    void doesNotHavePermission_shouldReadEffectivePermissionAndReturnSelf()
    {
        // setup
        when(gateway.hasPermission(PLAYER_ID, "example.use")).thenReturn(false);
        final var assertion = assertThat(player);

        // execute
        final var result = assertion.doesNotHavePermission("example.use");

        // verify
        assertThat(result).isSameAs(assertion);
        verify(gateway).hasPermission(PLAYER_ID, "example.use");
    }

    @Test
    void hasPermission_shouldReportPlayerAndMissingNode()
    {
        // setup
        when(gateway.hasPermission(PLAYER_ID, "example.use")).thenReturn(false);

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(player).hasPermission("example.use"));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("tester", "example.use", "was false");
    }

    @Test
    void doesNotHavePermission_shouldReportUnexpectedGrant()
    {
        // setup
        when(gateway.hasPermission(PLAYER_ID, "example.use")).thenReturn(true);

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(player).doesNotHavePermission("example.use"));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("tester", "example.use", "was true");
    }

    @Test
    void hasPermission_shouldRetryFreshEffectivePermission()
    {
        // setup
        when(gateway.hasPermission(PLAYER_ID, "example.use")).thenReturn(false, true);
        final var assertion = assertThat(player);

        // execute
        eventually(Duration.ofSeconds(2), () -> assertion.hasPermission("example.use"));

        // verify
        verify(gateway, times(2)).hasPermission(PLAYER_ID, "example.use");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void hasPermission_shouldRejectBlankNodeBeforeQuery(String node)
    {
        // setup
        final var assertion = assertThat(player);

        // execute
        final Throwable failure = catchThrowable(() -> assertion.hasPermission(node));

        // verify
        assertThat(failure)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("permission");
        verifyNoInteractions(gateway);
    }

    @Test
    @SuppressWarnings("NullAway") // Deliberately tests the non-null input boundary.
    void doesNotHavePermission_shouldRejectNullNodeBeforeQuery()
    {
        // setup
        final var assertion = assertThat(player);

        // execute
        final Throwable failure = catchThrowable(() -> assertion.doesNotHavePermission(null));

        // verify
        assertThat(failure)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("permission");
        verifyNoInteractions(gateway);
    }

    @Test
    void hasOpenMenu_shouldObserveOpeningAndClosingOnRetainedSubject()
    {
        // setup
        when(gateway.menuSnapshot(PLAYER_ID)).thenReturn(
            new MenuSnapshot(false, "", List.of()),
            new MenuSnapshot(true, "Menu", List.of()),
            new MenuSnapshot(false, "", List.of())
        );
        final var assertion = assertThat(player);

        // execute
        assertion
            .doesNotHaveOpenMenu()
            .hasOpenMenu()
            .doesNotHaveOpenMenu();

        // verify
        verify(gateway, times(3)).menuSnapshot(PLAYER_ID);
    }

    @Test
    void hasOpenMenu_shouldReportPlayerWhenNoMenuIsOpen()
    {
        // setup
        when(gateway.menuSnapshot(PLAYER_ID)).thenReturn(new MenuSnapshot(false, "", List.of()));

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(player).hasOpenMenu());

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("tester", "no menu was open");
        verify(gateway).menuSnapshot(PLAYER_ID);
    }

    @Test
    void doesNotHaveOpenMenu_shouldReportUnexpectedOpenMenu()
    {
        // setup
        when(gateway.menuSnapshot(PLAYER_ID)).thenReturn(new MenuSnapshot(true, "Menu", List.of()));

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(player).doesNotHaveOpenMenu());

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("tester", "a menu was open");
        verify(gateway).menuSnapshot(PLAYER_ID);
    }

    @Test
    void doesNotHaveOpenMenu_shouldPropagateFailedRead()
    {
        // setup
        final var expected = new IllegalStateException("Player is unavailable.");
        when(gateway.menuSnapshot(PLAYER_ID)).thenThrow(expected);

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(player).doesNotHaveOpenMenu());

        // verify
        assertThat(failure).isSameAs(expected);
    }

    @Test
    void hasPermission_shouldPropagateFailedRead()
    {
        // setup
        final var expected = new IllegalStateException("Framework is closed.");
        when(gateway.hasPermission(PLAYER_ID, "example.use")).thenThrow(expected);

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(player).hasPermission("example.use"));

        // verify
        assertThat(failure).isSameAs(expected);
    }

    @Test
    void hasPermission_shouldRejectNullPlayer()
    {
        // setup
        final var assertion = new PlayerHandleAssert(null);

        // execute
        final Throwable failure = catchThrowable(() -> assertion.hasPermission("example.use"));

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void hasClickableChatText_shouldRejectUnrelatedSiblingClickAction()
    {
        // setup
        when(gateway.playerChatComponents(PLAYER_ID)).thenReturn(
            List.of(
                new ChatComponentSnapshot(
                    """
                        {"text":"","extra":[{"text":"Open"},{"text":"Close",
                          "click_event":{"action":"run_command","command":"/close"}}]}
                        """
                )
            )
        );

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(player).hasClickableChatText("Open"));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("tester");
        verify(gateway).playerChatComponents(PLAYER_ID);
    }

    @Test
    void chatComponents_shouldCaptureFrozenListsAndReadAgainOnNavigation()
    {
        // setup
        final var component = new ChatComponentSnapshot("{\"text\":\"hello\"}");
        when(gateway.playerChatComponents(PLAYER_ID)).thenReturn(List.of(), List.of(component));
        final var assertion = assertThat(player);

        // execute
        final var earlier = assertion.chatComponents();
        final var later = assertion.chatComponents();

        // verify
        earlier.isEmpty();
        later.containsExactly(component);
        verify(gateway, times(2)).playerChatComponents(PLAYER_ID);
    }
}
