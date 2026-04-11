package nl.pim16aap2.lightkeeper.runtime.agent;

import java.util.Map;

/**
 * Agent request envelope.
 *
 * @param requestId
 *     Unique request id.
 * @param action
 *     Agent action.
 * @param arguments
 *     Request arguments.
 */
public record AgentRequest(
    String requestId,
    AgentAction action,
    Map<String, String> arguments
)
{
}
