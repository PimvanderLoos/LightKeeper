package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.CancelNextEvents;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListener;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListener;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEventActionsTest
{
    private static AgentEventActions createEventActions(AgentEventCapture eventCapture)
    {
        return new AgentEventActions(eventCapture);
    }

    @Test
    void handleRegisterEventListener_shouldThrowWhenClassIsBlank()
    {
        // setup
        final AgentEventActions actions = createEventActions(mock());

        // execute + verify
        assertThatThrownBy(() ->
            actions.handleRegisterEventListener(new RegisterEventListener.Command("req-1", "")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventClassName");
    }

    @Test
    void handleRegisterEventListener_shouldThrowWhenClassIsNotBukkitEvent()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Class 'java.lang.String' is not a Bukkit Event."))
            .when(eventCapture).registerListener("java.lang.String");

        // execute + verify
        assertThatThrownBy(() ->
            actions.handleRegisterEventListener(new RegisterEventListener.Command("req-2", "java.lang.String")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a Bukkit Event");
    }

    @Test
    void handleRegisterEventListener_shouldThrowWhenClassIsNotFound()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);
        org.mockito.Mockito.doThrow(new ClassNotFoundException("com.example.NonExistent"))
            .when(eventCapture).registerListener("com.example.NonExistent");

        // execute + verify
        assertThatThrownBy(() -> actions.handleRegisterEventListener(
            new RegisterEventListener.Command("req-cnf", "com.example.NonExistent")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Event class not found: com.example.NonExistent");
    }

    @Test
    void handleRegisterEventListener_shouldSucceedWhenClassIsRegistered()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);

        // execute
        final RegisterEventListener.Response response = actions.handleRegisterEventListener(
            new RegisterEventListener.Command("req-3", "org.bukkit.event.Event"));

        // verify
        assertThat(response).isNotNull();
        verify(eventCapture).registerListener("org.bukkit.event.Event");
    }

    @Test
    void handleGetCapturedEvents_shouldThrowWhenClassIsBlank()
    {
        // setup
        final AgentEventActions actions = createEventActions(mock());

        // execute + verify
        assertThatThrownBy(() -> actions.handleGetCapturedEvents(new GetCapturedEvents.Command("req-4", "")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventClassName");
    }

    @Test
    void handleGetCapturedEvents_shouldReturnCapturedEvents()
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);
        final GetCapturedEvents.CapturedEvent capturedEvent = new GetCapturedEvents.CapturedEvent(
            3L, Map.of("isCancelled", new IProtocolValue.PBool(true)));
        when(eventCapture.getCapturedEvents("org.bukkit.event.Event"))
            .thenReturn(List.of(capturedEvent));

        // execute
        final GetCapturedEvents.Response response = actions.handleGetCapturedEvents(
            new GetCapturedEvents.Command("req-5", "org.bukkit.event.Event"));

        // verify
        assertThat(response.events()).containsExactly(capturedEvent);
    }

    @Test
    void handleCancelNextEvents_shouldSucceedAndDelegateToEventCapture()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);

        // execute
        final CancelNextEvents.Response response = actions.handleCancelNextEvents(
            new CancelNextEvents.Command("req-cancel", "org.bukkit.event.Event", 2));

        // verify
        assertThat(response).isNotNull();
        verify(eventCapture).cancelNextEvents("org.bukkit.event.Event", 2);
    }

    @Test
    void handleCancelNextEvents_shouldMapRearmToInvalidArgument()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = mock(AgentEventCapture.class);
        doThrow(new IllegalStateException("already armed"))
            .when(eventCapture).cancelNextEvents("org.example.Evt", 2);
        final AgentEventActions eventActions = new AgentEventActions(eventCapture);
        final CancelNextEvents.Command command = new CancelNextEvents.Command("req-1", "org.example.Evt", 2);

        // execute
        final Throwable thrown = catchThrowable(() -> eventActions.handleCancelNextEvents(command));

        // verify - caller error surfaces as IllegalArgumentException (INVALID_ARGUMENT), not a server failure
        assertThat(thrown)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already armed");
    }

    @Test
    void handleCancelNextEvents_shouldThrowWhenClassIsNotFound()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);
        org.mockito.Mockito.doThrow(new ClassNotFoundException("com.example.NonExistent"))
            .when(eventCapture).cancelNextEvents("com.example.NonExistent", 1);

        // execute + verify
        assertThatThrownBy(() -> actions.handleCancelNextEvents(
            new CancelNextEvents.Command("req-cancel-cnf", "com.example.NonExistent", 1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Event class not found: com.example.NonExistent");
    }

    @Test
    void handleClearCapturedEvents_shouldSucceedAndClearWhenClassIsNotBlank()
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);

        // execute
        final ClearCapturedEvents.Response response = actions.handleClearCapturedEvents(
            new ClearCapturedEvents.Command("req-6", "org.bukkit.event.Event"));

        // verify
        assertThat(response).isNotNull();
        verify(eventCapture).clearCapturedEvents("org.bukkit.event.Event");
    }

    @Test
    void handleClearCapturedEvents_shouldThrowWhenClassIsBlank()
    {
        // setup
        final AgentEventActions actions = createEventActions(mock());

        // execute + verify
        assertThatThrownBy(() ->
            actions.handleClearCapturedEvents(new ClearCapturedEvents.Command("req-7", "")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventClassName");
    }

    @Test
    void handleUnregisterEventListener_shouldSucceedAndUnregisterWhenClassIsNotBlank()
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);

        // execute
        final UnregisterEventListener.Response response = actions.handleUnregisterEventListener(
            new UnregisterEventListener.Command("req-8", "org.bukkit.event.Event"));

        // verify
        assertThat(response).isNotNull();
        verify(eventCapture).unregisterListener("org.bukkit.event.Event");
    }

    @Test
    void handleUnregisterEventListener_shouldThrowWhenClassIsBlank()
    {
        // setup
        final AgentEventActions actions = createEventActions(mock());

        // execute + verify
        assertThatThrownBy(() ->
            actions.handleUnregisterEventListener(new UnregisterEventListener.Command("req-9", "")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventClassName");
    }
}
