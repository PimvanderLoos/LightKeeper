package nl.pim16aap2.lightkeeper.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Shared runtime protocol constants for LightKeeper.
 */
public final class RuntimeProtocol
{
    private static final String METADATA_RESOURCE = "/nl/pim16aap2/lightkeeper/runtime/lightkeeper-runtime.properties";
    private static final String SUPPORTED_MINECRAFT_VERSION_KEY = "supportedMinecraftVersion";

    /**
     * Runtime protocol version understood by the framework and in-server agent.
     *
     * <p>Increment this whenever the agent wire format, command set, or response semantics change
     * in a backward-incompatible way. Both the framework and the agent must agree on this value;
     * a mismatch causes an {@code HANDSHAKE} failure.
     */
    public static final int VERSION = 2;

    /**
     * Minecraft server version supported by this LightKeeper build.
     */
    public static final String SUPPORTED_MINECRAFT_VERSION = loadSupportedMinecraftVersion();

    /**
     * Default maximum time, in seconds, the agent waits for a scheduled synchronous server operation before
     * reporting {@code TIMEOUT}. Shared so the client can derive a strictly larger response timeout.
     */
    public static final long DEFAULT_SYNC_OPERATION_TIMEOUT_SECONDS = 120L;

    /**
     * Safety margin, in milliseconds, added to the agent-side operation timeout to derive the client's response
     * timeout. Guarantees the agent reports its own detailed {@code TIMEOUT} before the client watchdog gives up.
     */
    public static final long CLIENT_RESPONSE_TIMEOUT_MARGIN_MILLIS = 30_000L;

    /**
     * System property containing the Unix domain socket path used by the agent.
     */
    public static final String PROPERTY_SOCKET_PATH = "lightkeeper.agent.socketPath";
    /**
     * System property containing the runtime authentication token expected by the agent.
     */
    public static final String PROPERTY_AUTH_TOKEN = "lightkeeper.agent.authToken";
    /**
     * System property containing the runtime protocol version expected by the agent.
     */
    public static final String PROPERTY_PROTOCOL_VERSION = "lightkeeper.agent.protocolVersion";
    /**
     * System property containing the expected SHA-256 hash of the embedded agent JAR.
     */
    public static final String PROPERTY_EXPECTED_AGENT_SHA256 = "lightkeeper.agent.expectedSha256";
    /**
     * System property containing the synchronous operation timeout in seconds.
     */
    public static final String PROPERTY_SYNC_OPERATION_TIMEOUT_SECONDS =
        "lightkeeper.agent.syncOperationTimeoutSeconds";

    private RuntimeProtocol()
    {
    }

    private static String loadSupportedMinecraftVersion()
    {
        final Properties properties = new Properties();
        try (InputStream inputStream = RuntimeProtocol.class.getResourceAsStream(METADATA_RESOURCE))
        {
            if (inputStream == null)
                throw new IllegalStateException("Missing runtime metadata resource '" + METADATA_RESOURCE + "'.");

            properties.load(inputStream);
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException("Failed to read runtime metadata resource '" + METADATA_RESOURCE + "'.",
                exception);
        }

        final String supportedMinecraftVersion = properties.getProperty(SUPPORTED_MINECRAFT_VERSION_KEY, "").trim();
        if (supportedMinecraftVersion.isEmpty())
        {
            throw new IllegalStateException(
                "Runtime metadata resource '%s' does not define '%s'."
                    .formatted(METADATA_RESOURCE, SUPPORTED_MINECRAFT_VERSION_KEY)
            );
        }
        return supportedMinecraftVersion;
    }
}
