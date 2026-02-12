package nl.pim16aap2.lightkeeper.agent.paper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.java.JavaPlugin;

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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Paper plugin that exposes a UDS control channel for LightKeeper tests.
 */
public final class PaperLightkeeperAgentPlugin extends JavaPlugin
{
    private static final long SYNC_OPERATION_TIMEOUT_SECONDS = 30L;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ExecutorService acceptExecutor = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().name("lightkeeper-agent-accept-", 0).factory()
    );
    private ExecutorService requestExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean running;
    private Path socketPath = Path.of("lightkeeper-agent.sock");
    private String authToken = "";
    private String protocolVersion = RuntimeProtocol.VERSION;
    private String expectedAgentSha256 = "";
    private ServerSocketChannel serverSocketChannel;

    @Override
    public void onEnable()
    {
        try
        {
            initializeConfiguration();
            startServer();
        }
        catch (Exception exception)
        {
            getLogger().severe("Failed to start LightKeeper Paper agent: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable()
    {
        running = false;
        closeQuietly(serverSocketChannel);

        requestExecutor.shutdownNow();
        acceptExecutor.shutdownNow();

        if (socketPath != null)
        {
            try
            {
                Files.deleteIfExists(socketPath);
            }
            catch (IOException exception)
            {
                getLogger().warning("Failed to delete agent socket path '" + socketPath + "'.");
            }
        }
    }

    private void initializeConfiguration()
    {
        final String configuredSocketPath = requireNonBlankProperty(RuntimeProtocol.PROPERTY_SOCKET_PATH);
        socketPath = Path.of(configuredSocketPath).toAbsolutePath();
        authToken = requireNonBlankProperty(RuntimeProtocol.PROPERTY_AUTH_TOKEN);
        protocolVersion = requireNonBlankProperty(RuntimeProtocol.PROPERTY_PROTOCOL_VERSION);
        expectedAgentSha256 = System.getProperty(RuntimeProtocol.PROPERTY_EXPECTED_AGENT_SHA256, "");
    }

    private void startServer()
        throws IOException
    {
        if (socketPath.getParent() != null)
            Files.createDirectories(socketPath.getParent());
        Files.deleteIfExists(socketPath);

        serverSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        serverSocketChannel.bind(UnixDomainSocketAddress.of(socketPath));

        running = true;
        acceptExecutor.execute(this::acceptLoop);
        getLogger().info("LightKeeper agent started at socket path: " + socketPath);
    }

    private void acceptLoop()
    {
        while (running)
        {
            try
            {
                final SocketChannel socketChannel = serverSocketChannel.accept();
                requestExecutor.execute(() -> handleConnection(socketChannel));
            }
            catch (IOException exception)
            {
                if (running)
                    getLogger().warning("Agent accept loop failed: " + exception.getMessage());
            }
        }
    }

    private void handleConnection(SocketChannel socketChannel)
    {
        try (
            socketChannel;
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(Channels.newInputStream(socketChannel), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Channels.newOutputStream(socketChannel), StandardCharsets.UTF_8))
        )
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                final AgentResponse response = handleRequestLine(line);
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

    private AgentResponse handleRequestLine(String line)
    {
        try
        {
            final AgentRequest request = objectMapper.readValue(line, AgentRequest.class);
            return dispatchRequest(request);
        }
        catch (Exception exception)
        {
            return errorResponse(
                "unknown",
                "INVALID_REQUEST",
                "Failed to parse request: " + exception.getMessage()
            );
        }
    }

    private AgentResponse dispatchRequest(AgentRequest request)
    {
        final Map<String, String> arguments = request.arguments() == null ? Map.of() : request.arguments();
        final String requestId = Objects.requireNonNullElse(request.requestId(), "unknown");

        try
        {
            return switch (request.action())
            {
                case HANDSHAKE -> handleHandshake(requestId, arguments);
                case MAIN_WORLD -> handleMainWorld(requestId);
                case NEW_WORLD -> handleNewWorld(requestId, arguments);
                case EXECUTE_COMMAND -> handleExecuteCommand(requestId, arguments);
                case BLOCK_TYPE -> handleBlockType(requestId, arguments);
                case SET_BLOCK -> handleSetBlock(requestId, arguments);
            };
        }
        catch (Exception exception)
        {
            return errorResponse(requestId, "REQUEST_FAILED", exception.getMessage());
        }
    }

    private AgentResponse handleHandshake(String requestId, Map<String, String> arguments)
    {
        final String token = arguments.getOrDefault("token", "");
        final String clientProtocolVersion = arguments.getOrDefault("protocolVersion", "");
        final String clientAgentSha = arguments.getOrDefault("agentSha256", "");

        if (!authToken.equals(token))
            return errorResponse(requestId, "AUTH_FAILED", "Auth token mismatch.");
        if (!protocolVersion.equals(clientProtocolVersion))
            return errorResponse(requestId, "PROTOCOL_MISMATCH", "Runtime protocol version mismatch.");
        if (!expectedAgentSha256.isBlank() && !expectedAgentSha256.equalsIgnoreCase(clientAgentSha))
            return errorResponse(requestId, "AGENT_SHA_MISMATCH", "Agent SHA-256 mismatch.");

        return successResponse(requestId, Map.of(
            "protocolVersion", protocolVersion,
            "bukkitVersion", Bukkit.getBukkitVersion()
        ));
    }

    private AgentResponse handleMainWorld(String requestId)
        throws Exception
    {
        final World mainWorld = callOnMainThread(() -> Bukkit.getWorlds().getFirst());
        return successResponse(requestId, Map.of("worldName", mainWorld.getName()));
    }

    private AgentResponse handleNewWorld(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String worldName = arguments.getOrDefault("worldName", "").trim();
        if (worldName.isBlank())
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'worldName' must not be blank.");

        final String worldTypeValue = arguments.getOrDefault("worldType", "NORMAL");
        final String environmentValue = arguments.getOrDefault("environment", "NORMAL");
        final long seed = parseLong(arguments.getOrDefault("seed", "0"));

        final World world = callOnMainThread(() -> {
            final WorldCreator worldCreator = new WorldCreator(worldName);
            worldCreator.type(WorldType.valueOf(worldTypeValue.toUpperCase(Locale.ROOT)));
            worldCreator.environment(World.Environment.valueOf(environmentValue.toUpperCase(Locale.ROOT)));
            worldCreator.seed(seed);
            return worldCreator.createWorld();
        });

        if (world == null)
            return errorResponse(requestId, "WORLD_CREATE_FAILED", "Failed to create world '%s'.".formatted(worldName));

        return successResponse(requestId, Map.of("worldName", world.getName()));
    }

    private AgentResponse handleExecuteCommand(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String source = arguments.getOrDefault("source", "CONSOLE");
        final String rawCommand = arguments.getOrDefault("command", "");
        if (rawCommand.isBlank())
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'command' must not be blank.");

        if (!source.equalsIgnoreCase("CONSOLE"))
            return errorResponse(requestId, "UNSUPPORTED_SOURCE", "Only CONSOLE command source is supported in v1.");

        final String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean success = callOnMainThread(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));

        return successResponse(requestId, Map.of("success", success.toString()));
    }

    private AgentResponse handleBlockType(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String worldName = arguments.getOrDefault("worldName", "");
        final int x = parseInt(arguments.getOrDefault("x", "0"));
        final int y = parseInt(arguments.getOrDefault("y", "0"));
        final int z = parseInt(arguments.getOrDefault("z", "0"));

        final String materialName = callOnMainThread(() -> {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            return world.getBlockAt(x, y, z).getType().name();
        });

        return successResponse(requestId, Map.of("material", materialName));
    }

    private AgentResponse handleSetBlock(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String worldName = arguments.getOrDefault("worldName", "");
        final int x = parseInt(arguments.getOrDefault("x", "0"));
        final int y = parseInt(arguments.getOrDefault("y", "0"));
        final int z = parseInt(arguments.getOrDefault("z", "0"));
        final String materialName = arguments.getOrDefault("material", "");

        if (materialName.isBlank())
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'material' must not be blank.");

        final Material material = Material.matchMaterial(materialName);
        if (material == null)
            return errorResponse(requestId, "INVALID_ARGUMENT", "Unknown material '%s'.".formatted(materialName));

        final String setMaterial = callOnMainThread(() -> {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            world.getBlockAt(x, y, z).setType(material);
            return world.getBlockAt(x, y, z).getType().name();
        });

        return successResponse(requestId, Map.of("material", setMaterial));
    }

    private <T> T callOnMainThread(java.util.concurrent.Callable<T> callable)
        throws Exception
    {
        return Bukkit.getScheduler()
            .callSyncMethod(this, callable)
            .get(SYNC_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static int parseInt(String value)
    {
        return Integer.parseInt(value.trim());
    }

    private static long parseLong(String value)
    {
        return Long.parseLong(value.trim());
    }

    private String requireNonBlankProperty(String key)
    {
        final String value = System.getProperty(key, "");
        if (value.isBlank())
            throw new IllegalStateException("Required system property '%s' is missing.".formatted(key));
        return value;
    }

    private static AgentResponse successResponse(String requestId, Map<String, String> data)
    {
        return new AgentResponse(requestId, true, null, null, data);
    }

    private static AgentResponse errorResponse(String requestId, String errorCode, String message)
    {
        return new AgentResponse(requestId, false, errorCode, message, Map.of());
    }

    private static void closeQuietly(ServerSocketChannel channel)
    {
        if (channel == null)
            return;

        try
        {
            channel.close();
        }
        catch (IOException ignored)
        {
        }
    }
}
