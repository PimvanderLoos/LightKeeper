package nl.pim16aap2.lightkeeper.provisioning;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Represents the shared specification for preparing a Minecraft server baseline.
 * <p>
 * The base server is a cached version of the server that can be reused across different builds or runs of the server.
 * The test server is created by copying the base server to a new directory where it can be run independently.
 * <p>
 * If the base server is not available or needs to be recreated, it will be downloaded and prepared, which will include
 * downloading/compiling the server JAR file, configuring the server, and running it to ensure it is ready for use.
 *
 * @param serverVersion
 *     The version of the server to prepare.
 * @param jarCacheDirectoryRoot
 *     The directory where the server JAR files are cached.
 * @param baseServerCacheDirectoryRoot
 *     The directory where the base server files are cached.
 * @param serverWorkDirectoryRoot
 *     The directory where the server will run and store its data.
 * @param runtimeManifestPath
 *     Destination of the generated runtime manifest.
 * @param versionedCacheDirectories
 *     Whether to use versioned cache directories for the server JAR and base server.
 * @param jarCacheExpiryDays
 *     The number of days after which the cached server JAR files expire and should be rebuilt/redownloaded.
 * @param forceRebuildJar
 *     Whether to force the server JAR to be rebuilt/redownloaded, even if it is already cached.
 * @param baseServerCacheExpiryDays
 *     The number of days after which the cached base server files expire and should be recreated.
 * @param forceRecreateBaseServer
 *     Whether to force the base server to be recreated, even if it is already cached.
 * @param cleanupUnusedCacheDirectories
 *     Whether to prune expired sibling cache-key directories in jar/base cache parents during prepare.
 * @param serverInitTimeoutSeconds
 *     The number of seconds to wait for the server to initialize before timing out.
 * @param serverStopTimeoutSeconds
 *     The number of seconds to wait for the server to stop gracefully before timing out.
 * @param serverStartMaxAttempts
 *     Maximum number of attempts to initialize the prepared baseline.
 * @param memoryMb
 *     The amount of memory (in MB) allocated to the server.
 * @param javaExecutablePath
 *     Java executable selected by the caller for running the server.
 * @param extraJvmArgs
 *     Additional JVM arguments to pass when starting the server.
 * @param cacheKey
 *     Identity of the resolved server inputs used to separate cached baselines.
 * @param paperUserAgent
 *     HTTP User-Agent used for server downloads.
 * @param agentJarSha256
 *     Expected embedded agent checksum, or {@code null} for an agent-free base preparation.
 * @param agentAuthToken
 *     Runtime handshake token installed into the prepared manifest.
 * @param runtimeProtocolVersion
 *     Wire protocol version expected by the runtime agent.
 * @param agentCacheIdentity
 *     Embedded agent identity included in preparation provenance.
 */
public record ServerSpecification(
    String serverVersion,
    Path jarCacheDirectoryRoot,
    Path baseServerCacheDirectoryRoot,
    Path serverWorkDirectoryRoot,
    Path runtimeManifestPath,
    boolean versionedCacheDirectories,
    int jarCacheExpiryDays,
    boolean forceRebuildJar,
    int baseServerCacheExpiryDays,
    boolean forceRecreateBaseServer,
    boolean cleanupUnusedCacheDirectories,
    int serverInitTimeoutSeconds,
    int serverStopTimeoutSeconds,
    int serverStartMaxAttempts,
    int memoryMb,
    String javaExecutablePath,
    @Nullable String extraJvmArgs,
    String cacheKey,
    String paperUserAgent,
    @Nullable String agentJarSha256,
    String agentAuthToken,
    int runtimeProtocolVersion,
    String agentCacheIdentity
)
{
    /**
     * Copies this specification with different runtime output locations.
     *
     * @param newServerWorkDirectoryRoot
     *     Root directory for the prepared server.
     * @param newRuntimeManifestPath
     *     Runtime manifest destination.
     * @return The copied specification.
     */
    public ServerSpecification withRuntimeLocations(
        Path newServerWorkDirectoryRoot,
        Path newRuntimeManifestPath)
    {
        return new ServerSpecification(
            serverVersion,
            jarCacheDirectoryRoot,
            baseServerCacheDirectoryRoot,
            newServerWorkDirectoryRoot,
            newRuntimeManifestPath,
            versionedCacheDirectories,
            jarCacheExpiryDays,
            forceRebuildJar,
            baseServerCacheExpiryDays,
            forceRecreateBaseServer,
            cleanupUnusedCacheDirectories,
            serverInitTimeoutSeconds,
            serverStopTimeoutSeconds,
            serverStartMaxAttempts,
            memoryMb,
            javaExecutablePath,
            extraJvmArgs,
            cacheKey,
            paperUserAgent,
            agentJarSha256,
            agentAuthToken,
            runtimeProtocolVersion,
            agentCacheIdentity
        );
    }
}
