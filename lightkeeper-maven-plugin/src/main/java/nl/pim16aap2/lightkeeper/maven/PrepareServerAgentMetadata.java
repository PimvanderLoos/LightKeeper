package nl.pim16aap2.lightkeeper.maven;

import org.jspecify.annotations.Nullable;

/**
 * Agent identity values used for runtime manifest generation and cache keys.
 *
 * @param sha256
 *     SHA-256 digest of the configured agent JAR, or {@code null} if no agent is configured.
 * @param cacheIdentity
 *     Stable cache identity derived from the configured agent.
 */
record PrepareServerAgentMetadata(@Nullable String sha256, String cacheIdentity)
{
}
