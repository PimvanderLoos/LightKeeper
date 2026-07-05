package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListener;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListener;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
            .hasMessageContaining("com.example.NonExistent");
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
        when(eventCapture.getCapturedEvents("org.bukkit.event.Event"))
            .thenReturn(List.of(Map.of("isCancelled", "true")));

        // execute
        final GetCapturedEvents.Response response = actions.handleGetCapturedEvents(
            new GetCapturedEvents.Command("req-5", "org.bukkit.event.Event"));

        // verify
        assertThat(response.events()).containsExactly(Map.of("isCancelled", "true"));
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
