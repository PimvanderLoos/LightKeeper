package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEventsCommand;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEventsCommand;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListenerCommand;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListenerCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentEventActionsTest
{
    private static AgentEventActions createEventActions(AgentEventCapture eventCapture)
    {
        return new AgentEventActions(eventCapture, new ObjectMapper());
    }

    @Test
    void handleRegisterEventListener_shouldReturnInvalidArgumentWhenClassIsBlank()
    {
        // setup
        final AgentEventActions actions = createEventActions(mock());

        // execute
        final AgentResponse response =
            actions.handleRegisterEventListener(new RegisterEventListenerCommand("req-1", ""));

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("eventClassName");
    }

    @Test
    void handleRegisterEventListener_shouldReturnInvalidArgumentWhenClassIsNotBukkitEvent()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Class 'java.lang.String' is not a Bukkit Event."))
            .when(eventCapture).registerListener("java.lang.String");

        // execute
        final AgentResponse response =
            actions.handleRegisterEventListener(new RegisterEventListenerCommand("req-2", "java.lang.String"));

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("not a Bukkit Event");
    }

    @Test
    void handleRegisterEventListener_shouldReturnInvalidArgumentWhenClassIsNotFound()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);
        org.mockito.Mockito.doThrow(new ClassNotFoundException("com.example.NonExistent"))
            .when(eventCapture).registerListener("com.example.NonExistent");

        // execute
        final AgentResponse response =
            actions.handleRegisterEventListener(new RegisterEventListenerCommand("req-cnf", "com.example.NonExistent"));

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void handleRegisterEventListener_shouldSucceedWhenClassIsRegistered()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);

        // execute
        final AgentResponse response =
            actions.handleRegisterEventListener(new RegisterEventListenerCommand("req-3", "org.bukkit.event.Event"));

        // verify
        assertThat(response.success()).isTrue();
        verify(eventCapture).registerListener("org.bukkit.event.Event");
    }

    @Test
    void handleGetCapturedEvents_shouldReturnInvalidArgumentWhenClassIsBlank()
        throws Exception
    {
        // setup
        final AgentEventActions actions = createEventActions(mock());

        // execute
        final AgentResponse response =
            actions.handleGetCapturedEvents(new GetCapturedEventsCommand("req-4", ""));

        // verify
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.errorMessage()).contains("eventClassName");
    }

    @Test
    void handleGetCapturedEvents_shouldReturnSerializedEventsJson()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);
        when(eventCapture.getCapturedEvents("org.bukkit.event.Event"))
            .thenReturn(List.of(Map.of("isCancelled", "true")));

        // execute
        final AgentResponse response =
            actions.handleGetCapturedEvents(new GetCapturedEventsCommand("req-5", "org.bukkit.event.Event"));

        // verify
        assertThat(response.success()).isTrue();
        assertThat(response.data().get("eventsJson")).contains("isCancelled");
    }

    @Test
    void handleClearCapturedEvents_shouldSucceedAndClearWhenClassIsNotBlank()
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);

        // execute
        final AgentResponse response =
            actions.handleClearCapturedEvents(new ClearCapturedEventsCommand("req-6", "org.bukkit.event.Event"));

        // verify
        assertThat(response.success()).isTrue();
        verify(eventCapture).clearCapturedEvents("org.bukkit.event.Event");
    }

    @Test
    void handleClearCapturedEvents_shouldSucceedAndNoopWhenClassIsBlank()
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);

        // execute
        final AgentResponse response =
            actions.handleClearCapturedEvents(new ClearCapturedEventsCommand("req-7", ""));

        // verify - blank is silently ignored
        assertThat(response.success()).isTrue();
        verifyNoInteractions(eventCapture);
    }

    @Test
    void handleUnregisterEventListener_shouldSucceedAndUnregisterWhenClassIsNotBlank()
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);

        // execute
        final AgentResponse response =
            actions.handleUnregisterEventListener(
                new UnregisterEventListenerCommand("req-8", "org.bukkit.event.Event"));

        // verify
        assertThat(response.success()).isTrue();
        verify(eventCapture).unregisterListener("org.bukkit.event.Event");
    }

    @Test
    void handleUnregisterEventListener_shouldSucceedAndNoopWhenClassIsBlank()
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);

        // execute
        final AgentResponse response =
            actions.handleUnregisterEventListener(new UnregisterEventListenerCommand("req-9", ""));

        // verify - blank is silently ignored
        assertThat(response.success()).isTrue();
        verifyNoInteractions(eventCapture);
    }
}
