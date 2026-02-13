package nl.pim16aap2.lightkeeper.runtime;

import org.jspecify.annotations.Nullable;

import java.util.List;

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
 *     The runtime protocol version.
 * @param agentCacheIdentity
 *     The cache identity for the resolved agent artifact.
 * @param preloadedWorlds
 *     Worlds that should be loaded by the framework before test execution.
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
    String agentCacheIdentity,
    List<PreloadedWorld> preloadedWorlds
)
{
    public RuntimeManifest
    {
        preloadedWorlds = preloadedWorlds == null ? List.of() : List.copyOf(preloadedWorlds);
    }

    /**
     * World metadata for framework preloading.
     *
     * @param name
     *     World name.
     * @param environment
     *     Environment enum name.
     * @param worldType
     *     World type enum name.
     * @param seed
     *     World seed.
     */
    public record PreloadedWorld(
        String name,
        String environment,
        String worldType,
        long seed
    )
    {
    }
}
