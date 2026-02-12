package nl.pim16aap2.lightkeeper.runtime.agent;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Agent response envelope.
 *
 * @param requestId
 *     Correlated request id.
 * @param success
 *     Whether the request succeeded.
 * @param errorCode
 *     Optional error code.
 * @param errorMessage
 *     Optional error message.
 * @param data
 *     Optional response data.
 */
public record AgentResponse(
    String requestId,
    boolean success,
    @Nullable String errorCode,
    @Nullable String errorMessage,
    Map<String, String> data
)
{
}
