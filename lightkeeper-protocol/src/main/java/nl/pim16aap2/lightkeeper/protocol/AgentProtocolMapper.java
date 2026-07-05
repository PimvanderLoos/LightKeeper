package nl.pim16aap2.lightkeeper.protocol;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Creates consistently configured JSON mappers for both endpoints of the LightKeeper agent wire protocol.
 */
public final class AgentProtocolMapper
{
    private AgentProtocolMapper()
    {
    }

    /**
     * Creates a mapper that rejects null primitive values while tolerating additive unknown response fields.
     *
     * @return A new mapper configured for the LightKeeper wire protocol.
     */
    public static ObjectMapper create()
    {
        return JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    }
}
