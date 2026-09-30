package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.serverprovider.ServerProvider;

import java.nio.file.Path;

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
    PrepareServerResolvedServerSetup withRuntimeLocations(Path workDirectoryRoot, Path manifestPath)
    {
        return new PrepareServerResolvedServerSetup(
            serverProvider.withRuntimeLocations(workDirectoryRoot, manifestPath),
            manifestServerVersion,
            manifestBuildId,
            cacheKey,
            memoryMb
        );
    }
}
