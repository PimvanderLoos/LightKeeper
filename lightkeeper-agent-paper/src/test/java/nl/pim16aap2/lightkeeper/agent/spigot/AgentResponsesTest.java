package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AgentResponsesTest
{
    @Test
    void successResponse_shouldCreateSuccessfulResponseWithProvidedData()
    {
        // setup
        final String requestId = "request-1";
        final Map<String, String> data = Map.of("key", "value");

        // execute
        final AgentResponse response = AgentResponses.successResponse(requestId, data);

        // verify
        assertThat(response.requestId()).isEqualTo(requestId);
        assertThat(response.success()).isTrue();
        assertThat(response.errorCode()).isNull();
        assertThat(response.errorMessage()).isNull();
        assertThat(response.data()).containsExactlyEntriesOf(data);
    }

    @Test
    void errorResponse_shouldCreateFailedResponseWithEmptyData()
    {
        // setup
        final String requestId = "request-2";
        final String errorCode = "INVALID_ARGUMENT";
        final String message = "Argument 'x' must not be blank.";

        // execute
        final AgentResponse response = AgentResponses.errorResponse(requestId, errorCode, message);

        // verify
        assertThat(response.requestId()).isEqualTo(requestId);
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo(errorCode);
        assertThat(response.errorMessage()).isEqualTo(message);
        assertThat(response.data()).isEmpty();
    }
}
