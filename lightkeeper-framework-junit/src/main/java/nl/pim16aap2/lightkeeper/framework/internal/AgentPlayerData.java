package nl.pim16aap2.lightkeeper.framework.internal;

import java.util.UUID;

/**
 * Synthetic player data returned by the agent.
 */
record AgentPlayerData(UUID uniqueId, String name)
{
}
