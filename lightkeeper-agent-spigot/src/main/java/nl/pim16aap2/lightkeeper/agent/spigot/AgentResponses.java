package nl.pim16aap2.lightkeeper.agent.spigot;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.IAgentResponse;

/**
 * Factory helpers for canonical success/error protocol response JSON strings.
 *
 * <p>Success wire format: the domain response record serialized as JSON with an additional
 * {@code "success": true} field injected.
 *
 * <p>Error wire format: {@code {"requestId":"...","success":false,"errorCode":"...","errorMessage":"..."}}.
 */
final class AgentResponses
{
    /**
     * Utility class.
     */
    private AgentResponses()
    {
    }

    /**
     * Serializes a typed domain response with a {@code "success": true} field injected.
     *
     * @param objectMapper
     *     Jackson mapper used for serialization.
     * @param response
     *     Typed domain response record.
     * @return
     *     JSON string ready to write on the wire.
     * @throws JacksonException
     *     When serialization fails.
     */
    static String successJson(ObjectMapper objectMapper, IAgentResponse response)
        throws JacksonException
    {
        final ObjectNode node = objectMapper.valueToTree(response);
        node.put("success", true);
        return objectMapper.writeValueAsString(node);
    }

    /**
     * Produces a structured error JSON string.
     *
     * @param objectMapper
     *     Jackson mapper used for serialization.
     * @param requestId
     *     Identifier of the request being answered.
     * @param errorCode
     *     Stable machine-readable error identifier.
     * @param message
     *     Human-readable failure detail.
     * @return
     *     JSON string ready to write on the wire.
     * @throws JacksonException
     *     When serialization fails.
     */
    static String errorJson(ObjectMapper objectMapper, String requestId, AgentErrorCode errorCode, String message)
        throws JacksonException
    {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("requestId", requestId);
        node.put("success", false);
        node.put("errorCode", errorCode.wireCode());
        node.put("errorMessage", message);
        return objectMapper.writeValueAsString(node);
    }
}
