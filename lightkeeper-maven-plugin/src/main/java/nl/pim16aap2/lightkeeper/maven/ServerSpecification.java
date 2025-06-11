package nl.pim16aap2.lightkeeper.maven;

import java.nio.file.Path;

/**
 * Represents the specification for a server to be prepared by the Lightkeeper Maven plugin.
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
 * @param serverInitTimeoutSeconds
 *     The number of seconds to wait for the server to initialize before timing out.
 * @param serverStopTimeoutSeconds
 *     The number of seconds to wait for the server to stop gracefully before timing out.
 */
public record ServerSpecification(
    String serverVersion,
    Path jarCacheDirectoryRoot,
    Path baseServerCacheDirectoryRoot,
    Path serverWorkDirectoryRoot,
    boolean versionedCacheDirectories,
    int jarCacheExpiryDays,
    boolean forceRebuildJar,
    int baseServerCacheExpiryDays,
    boolean forceRecreateBaseServer,
    int serverInitTimeoutSeconds,
    int serverStopTimeoutSeconds
)
{
}
