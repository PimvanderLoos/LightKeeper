package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        final AgentResponse response = actions.handleRegisterEventListener("req-1", Map.of("eventClassName", ""));

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
        final AgentResponse response = actions.handleRegisterEventListener(
            "req-2", Map.of("eventClassName", "java.lang.String")
        );

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
        final AgentResponse response = actions.handleRegisterEventListener(
            "req-cnf", Map.of("eventClassName", "com.example.NonExistent")
        );

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
        final AgentResponse response = actions.handleRegisterEventListener(
            "req-3", Map.of("eventClassName", "org.bukkit.event.Event")
        );

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
        final AgentResponse response = actions.handleGetCapturedEvents("req-4", Map.of("eventClassName", ""));

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
        final AgentResponse response = actions.handleGetCapturedEvents(
            "req-5", Map.of("eventClassName", "org.bukkit.event.Event")
        );

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
        final AgentResponse response = actions.handleClearCapturedEvents(
            "req-6", Map.of("eventClassName", "org.bukkit.event.Event")
        );

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
        final AgentResponse response = actions.handleClearCapturedEvents("req-7", Map.of("eventClassName", ""));

        // verify - blank is silently ignored
        assertThat(response.success()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(eventCapture);
    }

    @Test
    void handleUnregisterEventListener_shouldSucceedAndUnregisterWhenClassIsNotBlank()
    {
        // setup
        final AgentEventCapture eventCapture = mock();
        final AgentEventActions actions = createEventActions(eventCapture);

        // execute
        final AgentResponse response = actions.handleUnregisterEventListener(
            "req-8", Map.of("eventClassName", "org.bukkit.event.Event")
        );

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
        final AgentResponse response = actions.handleUnregisterEventListener("req-9", Map.of("eventClassName", ""));

        // verify - blank is silently ignored
        assertThat(response.success()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(eventCapture);
    }
}
