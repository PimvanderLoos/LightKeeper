package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.MainWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AgentResponsesTest
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void successJson_shouldProduceJsonWithSuccessTrueAndDomainFields()
        throws Exception
    {
        // setup
        final MainWorld.Response response = new MainWorld.Response("request-1", "overworld");

        // execute
        final String json = AgentResponses.successJson(OBJECT_MAPPER, response);
        final JsonNode node = OBJECT_MAPPER.readTree(json);

        // verify
        assertThat(node.path("requestId").asText()).isEqualTo("request-1");
        assertThat(node.path("success").asBoolean()).isTrue();
        assertThat(node.path("worldName").asText()).isEqualTo("overworld");
    }

    @Test
    void errorJson_shouldProduceJsonWithSuccessFalseAndErrorFields()
        throws Exception
    {
        // setup
        final String requestId = "request-2";
        final AgentErrorCode errorCode = AgentErrorCode.INVALID_ARGUMENT;
        final String message = "Argument 'x' must not be blank.";

        // execute
        final String json = AgentResponses.errorJson(OBJECT_MAPPER, requestId, errorCode, message);
        final JsonNode node = OBJECT_MAPPER.readTree(json);

        // verify
        assertThat(node.path("requestId").asText()).isEqualTo(requestId);
        assertThat(node.path("success").asBoolean()).isFalse();
        assertThat(node.path("errorCode").asText()).isEqualTo(errorCode.wireCode());
        assertThat(node.path("errorMessage").asText()).isEqualTo(message);
    }
}
