package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.nms.v121r7.BotPlayerNmsAdapterV1_21_R7;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Spigot/Paper plugin entry point for the LightKeeper runtime agent.
 *
 * <p>The plugin opens a Unix domain socket server, receives JSON protocol requests, routes them to
 * action handlers, and returns JSON responses. It is the composition root for all agent components in this package and
 * owns startup/shutdown lifecycle behavior.
 */
public final class SpigotLightkeeperAgentPlugin extends JavaPlugin
{
    private static final System.Logger LOG = System.getLogger(SpigotLightkeeperAgentPlugin.class.getName());

    /**
     * Bukkit version prefix this plugin is compiled and validated against.
     */
    static final String SUPPORTED_MINECRAFT_VERSION = RuntimeProtocol.SUPPORTED_MINECRAFT_VERSION;
    /**
     * Prefix used to extract CraftBukkit package revision identifiers.
     */
    static final String CRAFTBUKKIT_PACKAGE_PREFIX = "org.bukkit.craftbukkit.";
    /**
     * Fallback NMS revision when runtime detection is unavailable.
     */
    private static final String DEFAULT_NMS_REVISION = "v1_21_R7";
    /**
     * Registered NMS adapter providers by CraftBukkit package revision.
     */
    private static final Map<String, Supplier<IBotPlayerNmsAdapter>> NMS_ADAPTERS = Map.of(
        DEFAULT_NMS_REVISION, BotPlayerNmsAdapterV1_21_R7::new
    );

    /**
     * Shared object mapper for request/response serialization.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Single-thread executor that accepts incoming socket connections.
     */
    private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().name("lightkeeper-agent-accept-", 0).factory()
    );
    /**
     * Per-connection request handling executor.
     */
    private final ExecutorService requestExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Indicates whether the socket server should keep accepting connections.
     */
    private volatile boolean running;
    /**
     * Active filesystem path for the Unix domain socket.
     */
    private Path socketPath = Path.of("lightkeeper-agent.sock");
    /**
     * Bound server socket channel, initialized during startup.
     */
    private @Nullable ServerSocketChannel serverSocketChannel;
    /**
     * Request dispatcher wired during startup.
     */
    private @Nullable AgentRequestDispatcher requestDispatcher;

    /**
     * Initializes agent configuration and socket server.
     */
    @Override
    public void onEnable()
    {
        try
        {
            final AgentConfiguration configuration = AgentConfiguration.fromSystemProperties();
            socketPath = configuration.socketPath();

            final String detectedNmsRevision = validateNmsCompatibility();
            final IBotPlayerNmsAdapter botPlayerNmsAdapter = createBotPlayerNmsAdapter(detectedNmsRevision);

            final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(this);
            final AgentSyntheticPlayerStore playerStore = new AgentSyntheticPlayerStore();
            final AgentMenuActions menuActions = new AgentMenuActions(
                mainThreadExecutor,
                playerStore,
                objectMapper
            );
            final AgentWorldActions worldActions = new AgentWorldActions(
                this,
                mainThreadExecutor,
                new AtomicLong(0L)
            );
            final AgentPlayerActions playerActions = new AgentPlayerActions(
                this,
                mainThreadExecutor,
                playerStore,
                objectMapper,
                botPlayerNmsAdapter
            );

            requestDispatcher = new AgentRequestDispatcher(
                objectMapper,
                worldActions,
                playerActions,
                menuActions,
                getLogger(),
                configuration.authToken(),
                configuration.protocolVersion(),
                configuration.expectedAgentSha256()
            );

            startTickLoop(worldActions);
            startServer(socketPath);
        }
        catch (Exception exception)
        {
            getLogger().severe("Failed to start LightKeeper Spigot agent: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Stops the socket server and cleans up synthetic player state.
     */
    @Override
    public void onDisable()
    {
        running = false;
        closeQuietly(serverSocketChannel);

        final AgentRequestDispatcher dispatcher = requestDispatcher;
        if (dispatcher != null)
            dispatcher.cleanupSyntheticPlayers();

        requestExecutor.shutdownNow();
        acceptExecutor.shutdownNow();

        try
        {
            Files.deleteIfExists(socketPath);
        }
        catch (IOException exception)
        {
            getLogger().warning("Failed to delete agent socket path '" + socketPath + "'.");
        }
    }

    /**
     * Validates Bukkit/NMS compatibility with supported adapters.
     *
     * @return Detected CraftBukkit revision, or {@code null} when no revision suffix is present.
     *
     * @throws IllegalStateException
     *     When Bukkit version or NMS revision is unsupported.
     */
    private @Nullable String validateNmsCompatibility()
    {
        final String bukkitVersion = Bukkit.getBukkitVersion();
        if (!bukkitVersion.equals(SUPPORTED_MINECRAFT_VERSION)
            && !bukkitVersion.startsWith(SUPPORTED_MINECRAFT_VERSION + "-"))
        {
            throw new IllegalStateException(
                "Unsupported Bukkit version '%s'. This agent only supports Minecraft version '%s'."
                    .formatted(bukkitVersion, SUPPORTED_MINECRAFT_VERSION)
            );
        }

        final String detectedNmsRevision = extractCraftBukkitRevision(
            Bukkit.getServer().getClass().getPackageName()
        );
        if (detectedNmsRevision != null && !NMS_ADAPTERS.containsKey(detectedNmsRevision))
        {
            throw new IllegalStateException(
                "Unsupported server NMS revision '%s'. Supported revisions: %s (Bukkit version: %s)."
                    .formatted(detectedNmsRevision, supportedNmsRevisions(), bukkitVersion)
            );
        }
        return detectedNmsRevision;
    }

    /**
     * Creates the NMS adapter matching the detected or fallback revision.
     *
     * @param detectedNmsRevision
     *     Detected CraftBukkit package revision, or {@code null} if unavailable.
     * @return Initialized NMS adapter.
     */
    private IBotPlayerNmsAdapter createBotPlayerNmsAdapter(@Nullable String detectedNmsRevision)
    {
        if (detectedNmsRevision == null)
        {
            getLogger().warning(
                "CraftBukkit revision could not be detected. Defaulting to %s adapter."
                    .formatted(DEFAULT_NMS_REVISION)
            );
        }

        final String resolvedRevision = detectedNmsRevision == null ? DEFAULT_NMS_REVISION : detectedNmsRevision;
        final Supplier<IBotPlayerNmsAdapter> adapterSupplier = NMS_ADAPTERS.get(resolvedRevision);
        if (adapterSupplier == null)
        {
            throw new IllegalStateException(
                "No NMS adapter registered for revision '%s'. Supported revisions: %s."
                    .formatted(resolvedRevision, supportedNmsRevisions())
            );
        }
        return adapterSupplier.get();
    }

    /**
     * Returns a sorted, comma-separated list of registered NMS revisions.
     *
     * @return Human-readable revision list.
     */
    private static String supportedNmsRevisions()
    {
        return NMS_ADAPTERS.keySet().stream()
            .sorted()
            .collect(Collectors.joining(", "));
    }

    /**
     * Extracts the CraftBukkit revision segment from a server package name.
     *
     * @param packageName
     *     Fully-qualified server package name.
     * @return Revision value such as {@code v1_21_R7}, or {@code null} when no explicit revision suffix exists.
     *
     * @throws IllegalStateException
     *     When the package name does not match expected CraftBukkit structure.
     */
    static @Nullable String extractCraftBukkitRevision(String packageName)
    {
        final String normalizedPackageName = packageName.trim();
        if ("org.bukkit.craftbukkit".equals(normalizedPackageName))
            return null;
        if (!normalizedPackageName.startsWith(CRAFTBUKKIT_PACKAGE_PREFIX))
        {
            throw new IllegalStateException(
                "Unexpected CraftBukkit package '%s'. Expected prefix '%s'."
                    .formatted(normalizedPackageName, CRAFTBUKKIT_PACKAGE_PREFIX)
            );
        }

        final int separatorIndex = normalizedPackageName.lastIndexOf('.');
        if (separatorIndex < 0 || separatorIndex == normalizedPackageName.length() - 1)
        {
            throw new IllegalStateException(
                "Unable to resolve CraftBukkit NMS revision from package '%s'."
                    .formatted(normalizedPackageName)
            );
        }

        final String revision = normalizedPackageName.substring(separatorIndex + 1);
        if (revision.equals("craftbukkit"))
            return null;
        return revision;
    }

    /**
     * Starts the scheduler task that increments the shared world action tick counter.
     *
     * @param worldActions
     *     World action handler that owns the tick counter.
     */
    private void startTickLoop(AgentWorldActions worldActions)
    {
        Bukkit.getScheduler().runTaskTimer(this, worldActions::incrementTick, 1L, 1L);
    }

    /**
     * Initializes and binds the Unix domain socket server.
     *
     * @param resolvedSocketPath
     *     Absolute socket path to bind.
     * @throws IOException
     *     When directory creation, socket cleanup, or binding fails.
     */
    private void startServer(Path resolvedSocketPath)
        throws IOException
    {
        if (resolvedSocketPath.getParent() != null)
            Files.createDirectories(resolvedSocketPath.getParent());
        Files.deleteIfExists(resolvedSocketPath);

        serverSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        serverSocketChannel.bind(UnixDomainSocketAddress.of(resolvedSocketPath));

        running = true;
        acceptExecutor.execute(this::acceptLoop);
        getLogger().info("LightKeeper agent started at socket path: " + resolvedSocketPath);
    }

    /**
     * Accept loop for incoming UDS connections.
     *
     * <p>Each accepted connection is handed to the request executor for independent processing.
     */
    private void acceptLoop()
    {
        while (running)
        {
            try
            {
                final ServerSocketChannel localServerSocketChannel = serverSocketChannel;
                if (localServerSocketChannel == null)
                {
                    if (running)
                    {
                        getLogger().warning(
                            "Agent accept loop is running without an initialized server socket channel."
                        );
                    }
                    return;
                }

                final SocketChannel socketChannel = localServerSocketChannel.accept();
                requestExecutor.execute(() -> handleConnection(socketChannel));
            }
            catch (IOException exception)
            {
                if (running)
                    getLogger().warning("Agent accept loop failed: " + exception.getMessage());
            }
        }
    }

    /**
     * Processes a single request/response stream over a connected socket channel.
     *
     * @param socketChannel
     *     Accepted socket channel.
     */
    private void handleConnection(SocketChannel socketChannel)
    {
        final AgentRequestDispatcher dispatcher = requestDispatcher;
        if (dispatcher == null)
        {
            getLogger().warning("Request dispatcher is not initialized; closing incoming connection.");
            closeQuietly(socketChannel);
            return;
        }

        try (
            socketChannel;
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(Channels.newInputStream(socketChannel), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Channels.newOutputStream(socketChannel), StandardCharsets.UTF_8))
        )
        {
            boolean handshakeCompleted = false;
            String line;
            while ((line = reader.readLine()) != null)
            {
                final AgentRequestDispatcher.RequestDispatchResult dispatchResult =
                    dispatcher.handleRequestLine(line, handshakeCompleted);
                handshakeCompleted = dispatchResult.handshakeCompleted();
                final AgentResponse response = dispatchResult.response();
                writer.write(objectMapper.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            }
        }
        catch (IOException exception)
        {
            if (running)
                getLogger().warning("Agent connection failed: " + exception.getMessage());
        }
    }

    /**
     * Closes a socket channel while suppressing close failures.
     *
     * @param channel
     *     Channel to close; may be {@code null}.
     */
    private static void closeQuietly(@Nullable SocketChannel channel)
    {
        if (channel == null)
            return;

        try
        {
            channel.close();
        }
        catch (IOException exception)
        {
            LOG.log(
                System.Logger.Level.WARNING,
                "Failed to close socket channel cleanly.",
                exception
            );
        }
    }

    /**
     * Closes a server socket channel while suppressing close failures.
     *
     * @param channel
     *     Channel to close; may be {@code null}.
     */
    private static void closeQuietly(@Nullable ServerSocketChannel channel)
    {
        if (channel == null)
            return;

        try
        {
            channel.close();
        }
        catch (IOException exception)
        {
            LOG.log(
                System.Logger.Level.WARNING,
                "Failed to close server socket channel cleanly.",
                exception
            );
        }
    }

}
