package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import java.nio.file.Path;

/**
 * Runtime values resolved during execution after input/config validation.
 */
record PrepareServerRuntimePreparation(
    PrepareServerAgentMetadata agentMetadata,
    String runtimeProtocolVersion,
    String agentAuthToken,
    Path udsSocketPath,
    PrepareServerResolvedServerSetup resolvedServerSetup
)
{
}
