package nl.pim16aap2.lightkeeper.runtime;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

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
 * @param memoryMb
 *     The memory (in MB) allocated to the server runtime.
 * @param udsSocketPath
 *     The UDS socket path for agent IPC.
 * @param agentAuthToken
 *     The short-lived agent auth token.
 * @param agentJar
 *     Optional path to the deployed agent jar.
 * @param agentJarSha256
 *     Optional SHA-256 hash for the agent jar.
 * @param runtimeProtocolVersion
 *     The runtime protocol version. Must exactly match {@link RuntimeProtocol#VERSION}.
 * @param agentCacheIdentity
 *     The cache identity for the resolved agent artifact.
 * @param extraJvmArgs
 *     Optional extra JVM arguments that must be applied when launching the test server runtime.
 * @param provisionedWorlds
 *     All worlds provisioned into the server directory by {@code prepare-server}, including templates that
 *     are not loaded on startup. The framework preloads the {@code loadOnStartup} entries before test
 *     execution and validates template names against the full list.
 */
public record RuntimeManifest(
    String serverType,
    String serverVersion,
    long paperBuildId,
    String cacheKey,
    String serverDirectory,
    String serverJar,
    int memoryMb,
    String udsSocketPath,
    String agentAuthToken,
    @Nullable String agentJar,
    @Nullable String agentJarSha256,
    int runtimeProtocolVersion,
    String agentCacheIdentity,
    @Nullable String extraJvmArgs,
    List<ProvisionedWorld> provisionedWorlds
)
{
    public RuntimeManifest
    {
        provisionedWorlds = provisionedWorlds == null ? List.of() : List.copyOf(provisionedWorlds);
    }

    /**
     * Relocates a complete prepared server into a private snapshot or session directory.
     *
     * @param destination
     *     New server directory containing copies of all prepared files.
     * @param socketPath
     *     Socket allocated to the new runtime.
     * @param authToken
     *     Handshake token allocated to the new runtime.
     * @return A manifest referring exclusively to files within the destination.
     * @throws IllegalArgumentException
     *     If a server or agent artifact is outside the prepared server directory.
     */
    public RuntimeManifest relocate(Path destination, Path socketPath, String authToken)
    {
        Objects.requireNonNull(destination, "destination may not be null.");
        Objects.requireNonNull(socketPath, "socketPath may not be null.");
        if (Objects.requireNonNull(authToken, "authToken may not be null.").isBlank())
        {
            throw new IllegalArgumentException("authToken may not be blank.");
        }
        final Path original = Path.of(serverDirectory).toAbsolutePath().normalize();
        final Path relocated = destination.toAbsolutePath().normalize();
        return new RuntimeManifest(
            serverType,
            serverVersion,
            paperBuildId,
            cacheKey,
            relocated.toString(),
            relocateArtifact(original, relocated, serverJar),
            memoryMb,
            socketPath.toAbsolutePath().normalize().toString(),
            authToken,
            agentJar == null ? null : relocateArtifact(original, relocated, agentJar),
            agentJarSha256,
            runtimeProtocolVersion,
            agentCacheIdentity,
            extraJvmArgs,
            provisionedWorlds
        );
    }

    private static String relocateArtifact(Path original, Path destination, String artifact)
    {
        final Path path = Path.of(artifact).toAbsolutePath().normalize();
        if (!path.startsWith(original) || path.equals(original))
        {
            throw new IllegalArgumentException(
                "Prepared artifact '%s' must be inside server directory '%s'.".formatted(path, original)
            );
        }
        return destination.resolve(original.relativize(path)).toString();
    }

    /**
     * Metadata of a world provisioned into the server directory.
     *
     * @param name
     *     World name.
     * @param environment
     *     Environment enum name.
     * @param worldType
     *     World type enum name.
     * @param seed
     *     World seed.
     * @param loadOnStartup
     *     Whether the framework loads this world before test execution.
     */
    public record ProvisionedWorld(
        String name,
        String environment,
        String worldType,
        long seed,
        boolean loadOnStartup
    )
    {
    }
}
