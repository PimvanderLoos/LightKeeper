package nl.pim16aap2.lightkeeper.agent.spigot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.MainWorld;
import nl.pim16aap2.lightkeeper.protocol.NewWorld;
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
        final MainWorld.Response response = new MainWorld.Response("overworld");

        // execute
        final String json =
            AgentResponses.successJson(OBJECT_MAPPER, "request-1", response, MainWorld.Response.class);
        final JsonNode node = OBJECT_MAPPER.readTree(json);

        // verify
        assertThat(node.path("requestId").asString()).isEqualTo("request-1");
        assertThat(node.path("success").asBoolean()).isTrue();
        assertThat(node.path("worldName").asString()).isEqualTo("overworld");
    }

    @Test
    void successJson_shouldThrowWhenResponseTypeDoesNotMatchExpected()
    {
        // setup
        final MainWorld.Response response = new MainWorld.Response("overworld");

        // execute + verify — a responseType()/handler mismatch must fail loudly, not corrupt the wire shape
        assertThatThrownBy(() ->
            AgentResponses.successJson(OBJECT_MAPPER, "request-1", response, NewWorld.Response.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Response type mismatch");
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
        assertThat(node.path("requestId").asString()).isEqualTo(requestId);
        assertThat(node.path("success").asBoolean()).isFalse();
        assertThat(node.path("errorCode").asString()).isEqualTo(errorCode.wireCode());
        assertThat(node.path("errorMessage").asString()).isEqualTo(message);
    }
}
