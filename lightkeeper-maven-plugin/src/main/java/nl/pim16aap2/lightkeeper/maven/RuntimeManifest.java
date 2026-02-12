package nl.pim16aap2.lightkeeper.maven;

import org.jspecify.annotations.Nullable;

/**
 * Runtime manifest written by {@code prepare-server}.
 *
 * @param serverType
 *     The server type.
 * @param serverVersion
 *     The resolved server version.
 * @param paperBuildId
 *     The resolved Paper build id.
 * @param cacheKey
 *     The resolved cache key.
 * @param serverDirectory
 *     The prepared target server directory.
 * @param serverJar
 *     The path to the prepared server jar.
 * @param udsSocketPath
 *     The UDS socket path for agent IPC.
 * @param agentAuthToken
 *     The short-lived agent auth token.
 * @param agentJar
 *     Optional path to the deployed agent jar.
 * @param agentJarSha256
 *     Optional SHA-256 hash for the agent jar.
 * @param runtimeProtocolVersion
 *     The LightKeeper runtime protocol version for manifest/agent compatibility.
 * @param agentCacheIdentity
 *     Cache identity used for the resolved agent artifact.
 */
public record RuntimeManifest(
    String serverType,
    String serverVersion,
    long paperBuildId,
    String cacheKey,
    String serverDirectory,
    String serverJar,
    String udsSocketPath,
    String agentAuthToken,
    @Nullable String agentJar,
    @Nullable String agentJarSha256,
    String runtimeProtocolVersion,
    String agentCacheIdentity
)
{
}
