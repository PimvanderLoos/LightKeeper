package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;

import java.util.Map;

/**
 * Factory helpers for canonical success/error protocol responses.
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
     * Creates a successful response with caller-provided data payload.
     *
     * @param requestId
     *     Identifier of the request being answered.
     * @param data
     *     Response payload entries.
     * @return
     *     Successful {@link AgentResponse}.
     */
    static AgentResponse successResponse(String requestId, Map<String, String> data)
    {
        return new AgentResponse(requestId, true, null, null, data);
    }

    /**
     * Creates an error response with a standardized empty data payload.
     *
     * @param requestId
     *     Identifier of the request being answered.
     * @param errorCode
     *     Stable machine-readable error identifier.
     * @param message
     *     Human-readable failure detail.
     * @return
     *     Failed {@link AgentResponse}.
     */
    static AgentResponse errorResponse(String requestId, String errorCode, String message)
    {
        return new AgentResponse(requestId, false, errorCode, message, Map.of());
    }

    /**
     * Creates an error response with caller-provided data payload.
     *
     * @param requestId
     *     Identifier of the request being answered.
     * @param errorCode
     *     Stable machine-readable error identifier.
     * @param message
     *     Human-readable failure detail.
     * @param data
     *     Structured error metadata payload.
     * @return
     *     Failed {@link AgentResponse}.
     */
    static AgentResponse errorResponse(String requestId, String errorCode, String message, Map<String, String> data)
    {
        return new AgentResponse(requestId, false, errorCode, message, data);
    }
}
