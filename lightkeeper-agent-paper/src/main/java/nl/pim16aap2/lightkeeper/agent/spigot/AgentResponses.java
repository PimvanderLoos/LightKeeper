package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;

import java.util.Map;

final class AgentResponses
{
    private AgentResponses()
    {
    }

    static AgentResponse successResponse(String requestId, Map<String, String> data)
    {
        return new AgentResponse(requestId, true, null, null, data);
    }

    static AgentResponse errorResponse(String requestId, String errorCode, String message)
    {
        return new AgentResponse(requestId, false, errorCode, message, Map.of());
    }
}
