package nl.pim16aap2.lightkeeper.maven;

import nl.pim16aap2.lightkeeper.maven.serverprovider.ServerProvider;

/**
 * Resolved server metadata and provider for the selected server type.
 */
record PrepareServerResolvedServerSetup(
    ServerProvider serverProvider,
    String manifestServerVersion,
    long manifestBuildId,
    String cacheKey,
    int memoryMb
)
{
}
