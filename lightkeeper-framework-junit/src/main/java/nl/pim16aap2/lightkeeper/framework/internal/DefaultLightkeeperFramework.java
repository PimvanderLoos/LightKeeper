package nl.pim16aap2.lightkeeper.framework.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.CommandSource;
import nl.pim16aap2.lightkeeper.framework.Condition;
import nl.pim16aap2.lightkeeper.framework.LightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import nl.pim16aap2.lightkeeper.framework.PlayerBuilder;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestReader;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default LightKeeper framework implementation.
 */
public final class DefaultLightkeeperFramework implements LightkeeperFramework
{
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration AGENT_CONNECT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(45);
    private static final String DEFAULT_WORLD_NAME_PREFIX = "lk_world_";
    private static final WorldSpec.WorldType DEFAULT_WORLD_TYPE = WorldSpec.WorldType.NORMAL;
    private static final WorldSpec.WorldEnvironment DEFAULT_WORLD_ENVIRONMENT = WorldSpec.WorldEnvironment.NORMAL;
    private static final long DEFAULT_WORLD_SEED = 0L;

    private final RuntimeManifest runtimeManifest;
    private final PaperServerHandle serverHandle;
    private UdsAgentClient agentClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Map<UUID, ResourceScope> createdPlayers = new ConcurrentHashMap<>();
    private final ThreadLocal<ResourceScope> activeResourceScope = ThreadLocal.withInitial(() -> ResourceScope.CLASS);

    private DefaultLightkeeperFramework(
        RuntimeManifest runtimeManifest,
        PaperServerHandle serverHandle)
    {
        this.runtimeManifest = runtimeManifest;
        this.serverHandle = serverHandle;
    }

    /**
     * Starts a framework from a runtime manifest path.
     *
     * @param runtimeManifestPath
     *     Path to runtime manifest.
     * @return Started framework.
     */
    public static DefaultLightkeeperFramework start(Path runtimeManifestPath)
    {
        final RuntimeManifest runtimeManifest;
        try
        {
            runtimeManifest = new RuntimeManifestReader().read(runtimeManifestPath);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                "Failed to read runtime manifest at '%s'.".formatted(runtimeManifestPath),
                exception
            );
        }

        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Path serverJar = Path.of(runtimeManifest.serverJar());
        if (!Files.isDirectory(serverDirectory))
            throw new IllegalStateException("Server directory '%s' does not exist.".formatted(serverDirectory));
        if (!Files.isRegularFile(serverJar))
            throw new IllegalStateException("Server jar '%s' does not exist.".formatted(serverJar));

        final Path diagnosticsDirectory = serverDirectory.resolveSibling("lightkeeper-diagnostics");
        final PaperServerHandle serverHandle = new PaperServerHandle(runtimeManifest, diagnosticsDirectory);
        serverHandle.start(STARTUP_TIMEOUT);

        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(runtimeManifest, serverHandle);
        framework.agentClient = framework.new UdsAgentClient(
            Path.of(runtimeManifest.udsSocketPath()),
            AGENT_CONNECT_TIMEOUT
        );
        framework.agentClient.handshake(
            runtimeManifest.agentAuthToken(),
            runtimeManifest.runtimeProtocolVersion(),
            Objects.requireNonNullElse(runtimeManifest.agentJarSha256(), "")
        );

        return framework;
    }

    @Override
    public WorldHandle mainWorld()
    {
        ensureOpen();
        return new WorldHandle(this, agentClient.mainWorld());
    }

    @Override
    public WorldHandle newWorld()
    {
        return newWorld(defaultWorldSpec());
    }

    @Override
    public WorldHandle newWorld(WorldSpec worldSpec)
    {
        ensureOpen();
        final WorldSpec validatedWorldSpec = validateWorldSpec(worldSpec);
        final String worldName = agentClient.newWorld(validatedWorldSpec);
        return new WorldHandle(this, worldName);
    }

    @Override
    public PlayerHandle createPlayer(String name, WorldHandle world)
    {
        return createPlayer(name, UUID.randomUUID(), world);
    }

    @Override
    public PlayerHandle createPlayer(String name, UUID uuid, WorldHandle world)
    {
        ensureOpen();
        final String trimmedName = validatePlayerName(name);
        Objects.requireNonNull(uuid, "uuid may not be null.");
        Objects.requireNonNull(world, "world may not be null.");
        final String worldName = Objects.requireNonNull(world.name(), "world.name may not be null.");

        final PlayerHandle playerHandle = agentClient.createPlayer(
            trimmedName,
            uuid,
            worldName,
            0.0D,
            0.0D,
            0.0D,
            null,
            null
        );
        createdPlayers.put(playerHandle.uniqueId(), activeResourceScope.get());
        return playerHandle;
    }

    @Override
    public PlayerBuilder buildPlayer()
    {
        ensureOpen();
        return new DefaultPlayerBuilder();
    }

    @Override
    public CommandResult executeCommand(CommandSource source, String command)
    {
        ensureOpen();
        if (source != CommandSource.CONSOLE)
            throw new IllegalArgumentException("Only CONSOLE command source is supported in v1.");

        final boolean success = agentClient.executeCommand(source, command);
        return new CommandResult(success, success ? "Command succeeded." : "Command failed.");
    }

    @Override
    public void waitUntil(Condition condition, Duration timeout)
    {
        ensureOpen();
        final long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline)
        {
            try
            {
                if (condition.evaluate())
                    return;
            }
            catch (Exception exception)
            {
                throw new IllegalStateException("Condition evaluation failed.", exception);
            }

            try
            {
                Thread.sleep(50L);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for condition.", exception);
            }
        }

        throw new IllegalStateException("Condition did not pass within timeout " + timeout + ".");
    }

    @Override
    public String blockType(WorldHandle world, Vector3Di position)
    {
        ensureOpen();
        return agentClient.blockType(world.name(), position);
    }

    @Override
    public void setBlock(WorldHandle world, Vector3Di position, String material)
    {
        ensureOpen();
        agentClient.setBlock(world.name(), position, material);
    }

    @Override
    public void executePlayerCommand(PlayerHandle player, String command)
    {
        ensureOpen();
        Objects.requireNonNull(player, "player may not be null.");
        final String trimmedCommand = Objects.requireNonNull(command, "command may not be null.").trim();
        if (trimmedCommand.isEmpty())
            throw new IllegalArgumentException("command may not be blank.");
        agentClient.executePlayerCommand(player.uniqueId(), trimmedCommand);
    }

    @Override
    public void placePlayerBlock(PlayerHandle player, String material, int x, int y, int z)
    {
        ensureOpen();
        Objects.requireNonNull(player, "player may not be null.");
        final String trimmedMaterial = Objects.requireNonNull(material, "material may not be null.").trim();
        if (trimmedMaterial.isEmpty())
            throw new IllegalArgumentException("material may not be blank.");
        agentClient.placePlayerBlock(player.uniqueId(), trimmedMaterial, x, y, z);
    }

    @Override
    public MenuSnapshot menuSnapshot(PlayerHandle player)
    {
        ensureOpen();
        Objects.requireNonNull(player, "player may not be null.");
        return agentClient.menuSnapshot(player.uniqueId());
    }

    @Override
    public void clickMenuSlot(PlayerHandle player, int slot)
    {
        ensureOpen();
        Objects.requireNonNull(player, "player may not be null.");
        if (slot < 0)
            throw new IllegalArgumentException("slot must be >= 0.");
        agentClient.clickMenuSlot(player.uniqueId(), slot);
    }

    @Override
    public void dragMenuSlots(PlayerHandle player, String materialKey, int... slots)
    {
        ensureOpen();
        Objects.requireNonNull(player, "player may not be null.");
        final String trimmedMaterial = Objects.requireNonNull(materialKey, "materialKey may not be null.").trim();
        if (trimmedMaterial.isEmpty())
            throw new IllegalArgumentException("materialKey may not be blank.");
        if (slots == null || slots.length == 0)
            throw new IllegalArgumentException("slots may not be empty.");
        for (int slot : slots)
        {
            if (slot < 0)
                throw new IllegalArgumentException("slot must be >= 0.");
        }
        agentClient.dragMenuSlots(player.uniqueId(), trimmedMaterial, slots);
    }

    @Override
    public void removePlayer(PlayerHandle player)
    {
        ensureOpen();
        Objects.requireNonNull(player, "player may not be null.");
        agentClient.removePlayer(player.uniqueId());
        createdPlayers.remove(player.uniqueId());
    }

    @Override
    public void waitTicks(int ticks)
    {
        ensureOpen();
        if (ticks < 0)
            throw new IllegalArgumentException("ticks must be >= 0.");
        if (ticks == 0)
            return;
        agentClient.waitTicks(ticks);
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
            return;

        try
        {
            cleanupPlayersByScope(null);
            agentClient.close();
        }
        finally
        {
            serverHandle.stop(SHUTDOWN_TIMEOUT);
        }
    }

    private void ensureOpen()
    {
        if (closed.get())
            throw new IllegalStateException("Framework is already closed.");
    }

    public void beginMethodScope()
    {
        ensureOpen();
        activeResourceScope.set(ResourceScope.METHOD);
    }

    public void endMethodScope()
    {
        ensureOpen();
        cleanupPlayersByScope(ResourceScope.METHOD);
        activeResourceScope.set(ResourceScope.CLASS);
    }

    private static WorldSpec validateWorldSpec(WorldSpec worldSpec)
    {
        Objects.requireNonNull(worldSpec, "worldSpec may not be null.");
        final String worldName = Objects.requireNonNull(worldSpec.name(), "worldSpec.name may not be null.").trim();
        if (worldName.isEmpty())
            throw new IllegalArgumentException("worldSpec.name may not be blank.");

        final WorldSpec.WorldType worldType =
            Objects.requireNonNull(worldSpec.worldType(), "worldSpec.worldType may not be null.");
        final WorldSpec.WorldEnvironment worldEnvironment =
            Objects.requireNonNull(worldSpec.environment(), "worldSpec.environment may not be null.");
        return new WorldSpec(worldName, worldType, worldEnvironment, worldSpec.seed());
    }

    private static WorldSpec defaultWorldSpec()
    {
        final String worldName = DEFAULT_WORLD_NAME_PREFIX + UUID.randomUUID().toString().replace("-", "");
        return new WorldSpec(worldName, DEFAULT_WORLD_TYPE, DEFAULT_WORLD_ENVIRONMENT, DEFAULT_WORLD_SEED);
    }

    private static String validatePlayerName(String name)
    {
        final String trimmedName = Objects.requireNonNull(name, "name may not be null.").trim();
        if (trimmedName.isEmpty())
            throw new IllegalArgumentException("name may not be blank.");
        if (trimmedName.length() > 16)
            throw new IllegalArgumentException("name may not exceed 16 characters.");
        return trimmedName;
    }

    private void cleanupPlayersByScope(ResourceScope scope)
    {
        final Set<UUID> playerIds = new HashSet<>(createdPlayers.keySet());
        for (UUID playerId : playerIds)
        {
            final ResourceScope playerScope = createdPlayers.get(playerId);
            if (playerScope == null)
                continue;
            if (scope != null && playerScope != scope)
                continue;
            try
            {
                agentClient.removePlayer(playerId);
            }
            catch (Exception ignored)
            {
            }
            finally
            {
                createdPlayers.remove(playerId);
            }
        }
    }

    private enum ResourceScope
    {
        CLASS,
        METHOD
    }

    private final class DefaultPlayerBuilder implements PlayerBuilder
    {
        private UUID uuid = UUID.randomUUID();
        private String name = "lk_bot_" + uuid.toString().substring(0, 8);
        private WorldHandle worldHandle;
        private Double x;
        private Double y;
        private Double z;
        private Double health;
        private final Set<String> permissions = new HashSet<>();

        @Override
        public PlayerBuilder withName(String name)
        {
            this.name = validatePlayerName(name);
            return this;
        }

        @Override
        public PlayerBuilder withRandomName()
        {
            this.uuid = UUID.randomUUID();
            this.name = "lk_bot_" + uuid.toString().substring(0, 8);
            return this;
        }

        @Override
        public PlayerBuilder inWorld(WorldHandle world)
        {
            this.worldHandle = Objects.requireNonNull(world, "world may not be null.");
            this.x = null;
            this.y = null;
            this.z = null;
            return this;
        }

        @Override
        public PlayerBuilder atLocation(WorldHandle world, double x, double y, double z)
        {
            this.worldHandle = Objects.requireNonNull(world, "world may not be null.");
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public PlayerBuilder atSpawn(WorldHandle world)
        {
            this.worldHandle = Objects.requireNonNull(world, "world may not be null.");
            this.x = null;
            this.y = null;
            this.z = null;
            return this;
        }

        @Override
        public PlayerBuilder withHealth(double health)
        {
            if (health <= 0.0D)
                throw new IllegalArgumentException("health must be > 0.");
            this.health = health;
            return this;
        }

        @Override
        public PlayerBuilder withPermissions(String... permissions)
        {
            if (permissions == null || permissions.length == 0)
                return this;
            Arrays.stream(permissions)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(permission -> !permission.isEmpty())
                .forEach(this.permissions::add);
            return this;
        }

        @Override
        public PlayerHandle build()
        {
            ensureOpen();
            final WorldHandle effectiveWorldHandle = Objects.requireNonNull(
                worldHandle,
                "world must be configured via inWorld/atLocation/atSpawn."
            );

            final PlayerHandle playerHandle = agentClient.createPlayer(
                validatePlayerName(name),
                uuid,
                effectiveWorldHandle.name(),
                x,
                y,
                z,
                health,
                permissions
            );
            createdPlayers.put(playerHandle.uniqueId(), activeResourceScope.get());
            return playerHandle;
        }
    }

    private final class UdsAgentClient implements AutoCloseable
    {
        private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private final Path socketPath;
        private final AtomicLong requestCounter = new AtomicLong(0L);
        private SocketChannel socketChannel;
        private BufferedReader reader;
        private BufferedWriter writer;

        private UdsAgentClient(Path socketPath, Duration connectTimeout)
        {
            this.socketPath = socketPath;
            connect(connectTimeout);
        }

        void handshake(String token, String protocolVersion, String agentSha256)
        {
            send(AgentAction.HANDSHAKE, Map.of(
                "token", token,
                "protocolVersion", protocolVersion,
                "agentSha256", agentSha256
            ));
        }

        String mainWorld()
        {
            final AgentResponse response = send(AgentAction.MAIN_WORLD, Map.of());
            return getRequiredData(response, "worldName");
        }

        String newWorld(WorldSpec worldSpec)
        {
            final AgentResponse response = send(AgentAction.NEW_WORLD, Map.of(
                "worldName", worldSpec.name(),
                "worldType", worldSpec.worldType().name(),
                "environment", worldSpec.environment().name(),
                "seed", Long.toString(worldSpec.seed())
            ));
            return getRequiredData(response, "worldName");
        }

        boolean executeCommand(CommandSource source, String command)
        {
            final AgentResponse response = send(AgentAction.EXECUTE_COMMAND, Map.of(
                "source", source.name(),
                "command", command
            ));
            return Boolean.parseBoolean(getRequiredData(response, "success"));
        }

        String blockType(String worldName, Vector3Di position)
        {
            final AgentResponse response = send(AgentAction.BLOCK_TYPE, Map.of(
                "worldName", worldName,
                "x", Integer.toString(position.x()),
                "y", Integer.toString(position.y()),
                "z", Integer.toString(position.z())
            ));
            return getRequiredData(response, "material");
        }

        void setBlock(String worldName, Vector3Di position, String material)
        {
            send(AgentAction.SET_BLOCK, Map.of(
                "worldName", worldName,
                "x", Integer.toString(position.x()),
                "y", Integer.toString(position.y()),
                "z", Integer.toString(position.z()),
                "material", material
            ));
        }

        PlayerHandle createPlayer(
            String name,
            UUID uuid,
            String worldName,
            Double x,
            Double y,
            Double z,
            Double health,
            Set<String> permissions)
        {
            final Map<String, String> arguments = new java.util.HashMap<>();
            arguments.put("name", name);
            arguments.put("uuid", uuid.toString());
            arguments.put("worldName", worldName);
            if (x != null && y != null && z != null)
            {
                arguments.put("x", Double.toString(x));
                arguments.put("y", Double.toString(y));
                arguments.put("z", Double.toString(z));
            }
            if (health != null)
                arguments.put("health", Double.toString(health));
            if (permissions != null && !permissions.isEmpty())
                arguments.put("permissions", String.join(",", permissions));

            final AgentResponse response = send(AgentAction.CREATE_PLAYER, arguments);
            final UUID createdUuid = UUID.fromString(getRequiredData(response, "uuid"));
            final String createdName = getRequiredData(response, "name");
            return new PlayerHandle(DefaultLightkeeperFramework.this, createdUuid, createdName);
        }

        void removePlayer(UUID uuid)
        {
            send(AgentAction.REMOVE_PLAYER, Map.of(
                "uuid", uuid.toString()
            ));
        }

        void executePlayerCommand(UUID uuid, String command)
        {
            send(AgentAction.EXECUTE_PLAYER_COMMAND, Map.of(
                "uuid", uuid.toString(),
                "command", command
            ));
        }

        void placePlayerBlock(UUID uuid, String material, int x, int y, int z)
        {
            send(AgentAction.PLACE_PLAYER_BLOCK, Map.of(
                "uuid", uuid.toString(),
                "material", material,
                "x", Integer.toString(x),
                "y", Integer.toString(y),
                "z", Integer.toString(z)
            ));
        }

        MenuSnapshot menuSnapshot(UUID uuid)
        {
            final AgentResponse response = send(AgentAction.GET_OPEN_MENU, Map.of(
                "uuid", uuid.toString()
            ));
            final boolean open = Boolean.parseBoolean(getRequiredData(response, "open"));
            if (!open)
                return new MenuSnapshot(false, "", List.of());

            final String title = getRequiredData(response, "title");
            final String itemsJson = response.data().getOrDefault("itemsJson", "[]");
            try
            {
                final MenuItemSnapshot[] items = objectMapper.readValue(itemsJson, MenuItemSnapshot[].class);
                return new MenuSnapshot(true, title, List.of(items));
            }
            catch (IOException exception)
            {
                throw new IllegalStateException("Failed to parse menu snapshot JSON.", exception);
            }
        }

        void clickMenuSlot(UUID uuid, int slot)
        {
            send(AgentAction.CLICK_MENU_SLOT, Map.of(
                "uuid", uuid.toString(),
                "slot", Integer.toString(slot)
            ));
        }

        void dragMenuSlots(UUID uuid, String materialKey, int... slots)
        {
            final String slotList = Arrays.stream(slots)
                .mapToObj(Integer::toString)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
            send(AgentAction.DRAG_MENU_SLOTS, Map.of(
                "uuid", uuid.toString(),
                "material", materialKey,
                "slots", slotList
            ));
        }

        void waitTicks(int ticks)
        {
            send(AgentAction.WAIT_TICKS, Map.of(
                "ticks", Integer.toString(ticks)
            ));
        }

        synchronized AgentResponse send(AgentAction action, Map<String, String> arguments)
        {
            final String requestId = Long.toString(requestCounter.incrementAndGet());
            final AgentRequest request = new AgentRequest(requestId, action, arguments);
            try
            {
                writer.write(objectMapper.writeValueAsString(request));
                writer.newLine();
                writer.flush();

                final String responseLine = reader.readLine();
                if (responseLine == null)
                    throw new IllegalStateException("Agent connection closed unexpectedly.");

                final AgentResponse response = objectMapper.readValue(responseLine, AgentResponse.class);
                if (!requestId.equals(response.requestId()))
                {
                    throw new IllegalStateException(
                        "Unexpected response id '%s' for request '%s'."
                            .formatted(response.requestId(), requestId)
                    );
                }

                if (!response.success())
                {
                    throw new IllegalStateException(
                        "Agent request failed. code=%s message=%s"
                            .formatted(response.errorCode(), response.errorMessage())
                    );
                }

                return response;
            }
            catch (IOException exception)
            {
                throw new IllegalStateException(
                    "Failed to communicate with agent via socket '%s'.".formatted(socketPath),
                    exception
                );
            }
        }

        @Override
        public synchronized void close()
        {
            try
            {
                if (socketChannel != null)
                    socketChannel.close();
            }
            catch (IOException ignored)
            {
            }
        }

        private void connect(Duration timeout)
        {
            final long deadline = System.nanoTime() + timeout.toNanos();
            Exception lastException = null;

            while (System.nanoTime() < deadline)
            {
                try
                {
                    final SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                    channel.connect(UnixDomainSocketAddress.of(socketPath));
                    this.socketChannel = channel;
                    this.reader = new BufferedReader(
                        new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8)
                    );
                    this.writer = new BufferedWriter(
                        new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8)
                    );
                    return;
                }
                catch (Exception exception)
                {
                    lastException = exception;
                    sleep(100L);
                }
            }

            throw new IllegalStateException(
                "Failed to connect to agent socket '%s' within timeout %s."
                    .formatted(socketPath, timeout),
                lastException
            );
        }

        private static String getRequiredData(AgentResponse response, String key)
        {
            final String value = response.data().get(key);
            if (value == null)
                throw new IllegalStateException("Missing response field '%s' from agent.".formatted(key));
            return value;
        }

        private static void sleep(long millis)
        {
            try
            {
                Thread.sleep(millis);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for agent connection.", exception);
            }
        }
    }

    private static final class PaperServerHandle
    {
        private final RuntimeManifest runtimeManifest;
        private final Path diagnosticsDirectory;
        private final List<String> outputLines = new ArrayList<>();
        private final CountDownLatch startedLatch = new CountDownLatch(1);
        private Process process;
        private Thread outputThread;

        private PaperServerHandle(RuntimeManifest runtimeManifest, Path diagnosticsDirectory)
        {
            this.runtimeManifest = runtimeManifest;
            this.diagnosticsDirectory = diagnosticsDirectory;
        }

        void start(Duration timeout)
        {
            final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
            final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
            final Path serverJar = Path.of(runtimeManifest.serverJar());

            final ProcessBuilder processBuilder = new ProcessBuilder(
                javaExecutable.toString(),
                "-Xmx1024M",
                "-Xms1024M",
                "-D" + RuntimeProtocol.PROPERTY_SOCKET_PATH + "=" + runtimeManifest.udsSocketPath(),
                "-D" + RuntimeProtocol.PROPERTY_AUTH_TOKEN + "=" + runtimeManifest.agentAuthToken(),
                "-D" + RuntimeProtocol.PROPERTY_PROTOCOL_VERSION + "=" + runtimeManifest.runtimeProtocolVersion(),
                "-D" + RuntimeProtocol.PROPERTY_EXPECTED_AGENT_SHA256 + "=" +
                    Objects.requireNonNullElse(runtimeManifest.agentJarSha256(), ""),
                "-jar",
                serverJar.toString(),
                "--nogui"
            );
            processBuilder.directory(serverDirectory.toFile());
            processBuilder.redirectErrorStream(true);

            try
            {
                process = processBuilder.start();
                outputThread = createOutputReaderThread(process, outputLines, startedLatch);
                outputThread.start();
                final boolean started = startedLatch.await(timeout.toSeconds(), TimeUnit.SECONDS);
                if (!started)
                {
                    writeDiagnostics("startup-timeout");
                    stop(Duration.ofSeconds(5));
                    throw new IllegalStateException(
                        "Paper server did not start within timeout. Tail:%n%s".formatted(outputTail())
                    );
                }
            }
            catch (Exception exception)
            {
                writeDiagnostics("startup-failure");
                stop(Duration.ofSeconds(5));
                throw new IllegalStateException("Failed to start Paper server.", exception);
            }
        }

        void stop(Duration timeout)
        {
            if (process == null)
                return;

            try
            {
                if (process.isAlive())
                {
                    try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)))
                    {
                        writer.write("stop");
                        writer.newLine();
                        writer.flush();
                    }

                    final boolean stopped = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
                    if (!stopped && process.isAlive())
                        process.destroyForcibly();
                }
            }
            catch (Exception exception)
            {
                if (process.isAlive())
                    process.destroyForcibly();
                writeDiagnostics("shutdown-failure");
            }

            if (outputThread != null)
            {
                try
                {
                    outputThread.join(TimeUnit.SECONDS.toMillis(5));
                }
                catch (InterruptedException exception)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private String outputTail()
        {
            synchronized (outputLines)
            {
                final int startIndex = Math.max(0, outputLines.size() - 40);
                return String.join(System.lineSeparator(), outputLines.subList(startIndex, outputLines.size()));
            }
        }

        private void writeDiagnostics(String reason)
        {
            try
            {
                Files.createDirectories(diagnosticsDirectory);
                final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                    .replace(":", "-");
                final Path bundleDirectory = diagnosticsDirectory.resolve("bundle-" + timestamp);
                Files.createDirectories(bundleDirectory);

                Files.writeString(bundleDirectory.resolve("reason.txt"), reason, StandardCharsets.UTF_8);
                Files.writeString(bundleDirectory.resolve("manifest-server-dir.txt"),
                    runtimeManifest.serverDirectory(), StandardCharsets.UTF_8);
                Files.writeString(bundleDirectory.resolve("manifest-socket-path.txt"),
                    runtimeManifest.udsSocketPath(), StandardCharsets.UTF_8);
                Files.writeString(bundleDirectory.resolve("manifest-protocol-version.txt"),
                    runtimeManifest.runtimeProtocolVersion(), StandardCharsets.UTF_8);
                Files.writeString(bundleDirectory.resolve("server-output.log"),
                    String.join(System.lineSeparator(), outputLines), StandardCharsets.UTF_8);
            }
            catch (IOException ignored)
            {
            }
        }

        private static Thread createOutputReaderThread(
            Process process,
            List<String> outputLines,
            CountDownLatch startedLatch)
        {
            return Thread.ofPlatform()
                .name("lightkeeper-paper-output-reader")
                .daemon(true)
                .unstarted(() -> {
                    try (BufferedReader reader =
                             new BufferedReader(
                                 new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
                    {
                        String line;
                        while ((line = reader.readLine()) != null)
                        {
                            synchronized (outputLines)
                            {
                                outputLines.add(line);
                            }

                            if (line.contains("Done (") && line.endsWith(")! For help, type \"help\""))
                                startedLatch.countDown();
                        }
                    }
                    catch (IOException ignored)
                    {
                    }
                });
        }
    }
}
