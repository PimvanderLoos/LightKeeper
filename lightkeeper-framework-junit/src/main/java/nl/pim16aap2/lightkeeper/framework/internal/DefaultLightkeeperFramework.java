package nl.pim16aap2.lightkeeper.framework.internal;

import lombok.extern.java.Log;
import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.CommandSource;
import nl.pim16aap2.lightkeeper.framework.Condition;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import nl.pim16aap2.lightkeeper.framework.IPlayerBuilder;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestReader;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default LightKeeper framework implementation.
 */
@Log
public final class DefaultLightkeeperFramework implements ILightkeeperFramework, IFrameworkGateway
{
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration AGENT_CONNECT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(45);
    private static final String DEFAULT_WORLD_NAME_PREFIX = "lk_world_";
    private static final WorldSpec.WorldType DEFAULT_WORLD_TYPE = WorldSpec.WorldType.NORMAL;
    private static final WorldSpec.WorldEnvironment DEFAULT_WORLD_ENVIRONMENT = WorldSpec.WorldEnvironment.NORMAL;
    private static final long DEFAULT_WORLD_SEED = 0L;

    private final RuntimeManifest runtimeManifest;
    private final PaperServerProcess paperServerProcess;
    private final UdsAgentClient agentClient;
    private final PlayerScopeRegistry playerScopeRegistry;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Inject
    DefaultLightkeeperFramework(
        RuntimeManifest runtimeManifest,
        PaperServerProcess paperServerProcess,
        UdsAgentClient agentClient,
        PlayerScopeRegistry playerScopeRegistry)
    {
        this.runtimeManifest = Objects.requireNonNull(runtimeManifest, "runtimeManifest may not be null.");
        this.paperServerProcess = Objects.requireNonNull(paperServerProcess, "paperServerProcess may not be null.");
        this.agentClient = Objects.requireNonNull(agentClient, "agentClient may not be null.");
        this.playerScopeRegistry = Objects.requireNonNull(playerScopeRegistry, "playerScopeRegistry may not be null.");
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
        final RuntimeManifest runtimeManifest = readRuntimeManifest(runtimeManifestPath);
        validateRuntimeManifest(runtimeManifest);

        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Path diagnosticsDirectory = serverDirectory.resolveSibling("lightkeeper-diagnostics");
        final PaperServerProcess paperServerProcess = new PaperServerProcess(runtimeManifest, diagnosticsDirectory);

        UdsAgentClient agentClient = null;
        try
        {
            log.info(() -> "LK_FRAMEWORK: Starting Paper server from '" + serverDirectory + "'.");
            paperServerProcess.start(STARTUP_TIMEOUT);

            agentClient = new UdsAgentClient(
                Path.of(runtimeManifest.udsSocketPath()),
                AGENT_CONNECT_TIMEOUT
            );
            agentClient.handshake(
                runtimeManifest.agentAuthToken(),
                runtimeManifest.runtimeProtocolVersion(),
                Objects.requireNonNullElse(runtimeManifest.agentJarSha256(), "")
            );

            final FrameworkInternalComponent component = DaggerFrameworkInternalComponent.factory().create(
                runtimeManifest,
                paperServerProcess,
                agentClient
            );
            final DefaultLightkeeperFramework framework = component.framework();
            framework.preloadConfiguredWorlds();
            return framework;
        }
        catch (Exception exception)
        {
            if (agentClient != null)
                agentClient.close();
            paperServerProcess.stop(SHUTDOWN_TIMEOUT);
            throw exception;
        }
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
        log.info(() -> "LK_FRAMEWORK: Created world '" + worldName + "'.");
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

        final AgentPlayerData createdPlayer = agentClient.createPlayer(
            trimmedName,
            uuid,
            worldName,
            0.0D,
            0.0D,
            0.0D,
            null,
            null
        );
        playerScopeRegistry.register(createdPlayer.uniqueId());
        log.info(() -> "LK_FRAMEWORK: Created player '%s' (%s) in world '%s'."
            .formatted(createdPlayer.name(), createdPlayer.uniqueId(), worldName));
        return new PlayerHandle(this, createdPlayer.uniqueId(), createdPlayer.name());
    }

    @Override
    public IPlayerBuilder buildPlayer()
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
        Objects.requireNonNull(condition, "condition may not be null.");
        Objects.requireNonNull(timeout, "timeout may not be null.");

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
    public String getBlock(String worldName, Vector3Di position)
    {
        ensureOpen();
        Objects.requireNonNull(worldName, "worldName may not be null.");
        Objects.requireNonNull(position, "position may not be null.");
        return agentClient.blockType(worldName, position);
    }

    @Override
    public void setBlock(String worldName, Vector3Di position, String material)
    {
        ensureOpen();
        Objects.requireNonNull(worldName, "worldName may not be null.");
        Objects.requireNonNull(position, "position may not be null.");
        final String trimmedMaterial = Objects.requireNonNull(material, "material may not be null.").trim();
        if (trimmedMaterial.isEmpty())
            throw new IllegalArgumentException("material may not be blank.");
        agentClient.setBlock(worldName, position, trimmedMaterial);
    }

    @Override
    public void executePlayerCommand(UUID playerId, String command)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        final String trimmedCommand = Objects.requireNonNull(command, "command may not be null.").trim();
        if (trimmedCommand.isEmpty())
            throw new IllegalArgumentException("command may not be blank.");
        agentClient.executePlayerCommand(playerId, trimmedCommand);
    }

    @Override
    public void placePlayerBlock(UUID playerId, String material, int x, int y, int z)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        final String trimmedMaterial = Objects.requireNonNull(material, "material may not be null.").trim();
        if (trimmedMaterial.isEmpty())
            throw new IllegalArgumentException("material may not be blank.");
        agentClient.placePlayerBlock(playerId, trimmedMaterial, x, y, z);
    }

    @Override
    public MenuSnapshot menuSnapshot(UUID playerId)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        return agentClient.menuSnapshot(playerId);
    }

    @Override
    public void clickMenuSlot(UUID playerId, int slot)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        if (slot < 0)
            throw new IllegalArgumentException("slot must be >= 0.");
        agentClient.clickMenuSlot(playerId, slot);
    }

    @Override
    public void dragMenuSlots(UUID playerId, String materialKey, int... slots)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        final String trimmedMaterial = Objects.requireNonNull(materialKey, "materialKey may not be null.").trim();
        if (trimmedMaterial.isEmpty())
            throw new IllegalArgumentException("materialKey may not be blank.");
        if (slots == null || slots.length == 0)
            throw new IllegalArgumentException("slots may not be empty.");
        for (final int slot : slots)
        {
            if (slot < 0)
                throw new IllegalArgumentException("slot must be >= 0.");
        }
        agentClient.dragMenuSlots(playerId, trimmedMaterial, slots);
    }

    @Override
    public void removePlayer(PlayerHandle player)
    {
        ensureOpen();
        Objects.requireNonNull(player, "player may not be null.");
        agentClient.removePlayer(player.uniqueId());
        playerScopeRegistry.unregister(player.uniqueId());
        log.info(() -> "LK_FRAMEWORK: Removed player '%s' (%s)."
            .formatted(player.name(), player.uniqueId()));
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
    public List<String> playerMessages(UUID playerId)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        return agentClient.playerMessages(playerId);
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
            return;

        try
        {
            log.info("LK_FRAMEWORK: Closing framework and cleaning up players.");
            playerScopeRegistry.cleanupAll(agentClient::removePlayer);
            agentClient.close();
        }
        finally
        {
            log.info("LK_FRAMEWORK: Stopping Paper server.");
            paperServerProcess.stop(SHUTDOWN_TIMEOUT);
        }
    }

    public void beginMethodScope()
    {
        ensureOpen();
        playerScopeRegistry.beginMethodScope();
    }

    public void endMethodScope()
    {
        ensureOpen();
        playerScopeRegistry.endMethodScope(agentClient::removePlayer);
    }

    private void preloadConfiguredWorlds()
    {
        for (final RuntimeManifest.PreloadedWorld preloadedWorld : runtimeManifest.preloadedWorlds())
        {
            final WorldSpec worldSpec = new WorldSpec(
                preloadedWorld.name(),
                WorldSpec.WorldType.valueOf(preloadedWorld.worldType()),
                WorldSpec.WorldEnvironment.valueOf(preloadedWorld.environment()),
                preloadedWorld.seed()
            );
            final String worldName = agentClient.newWorld(worldSpec);
            log.info(() -> "LK_FRAMEWORK: Preloaded world '%s' from runtime manifest.".formatted(worldName));
        }
    }

    private void ensureOpen()
    {
        if (closed.get())
            throw new IllegalStateException("Framework is already closed.");
    }

    private static RuntimeManifest readRuntimeManifest(Path runtimeManifestPath)
    {
        try
        {
            return new RuntimeManifestReader().read(runtimeManifestPath);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                "Failed to read runtime manifest at '%s'.".formatted(runtimeManifestPath),
                exception
            );
        }
    }

    private static void validateRuntimeManifest(RuntimeManifest runtimeManifest)
    {
        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Path serverJar = Path.of(runtimeManifest.serverJar());
        if (!Files.isDirectory(serverDirectory))
            throw new IllegalStateException("Server directory '%s' does not exist.".formatted(serverDirectory));
        if (!Files.isRegularFile(serverJar))
            throw new IllegalStateException("Server jar '%s' does not exist.".formatted(serverJar));
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

    private final class DefaultPlayerBuilder implements IPlayerBuilder
    {
        private UUID uuid = UUID.randomUUID();
        private String name = "lk_bot_" + uuid.toString().substring(0, 8);
        private @Nullable WorldHandle worldHandle;
        private @Nullable Double x;
        private @Nullable Double y;
        private @Nullable Double z;
        private @Nullable Double health;
        private final Set<String> permissions = new HashSet<>();

        @Override
        public IPlayerBuilder withName(String name)
        {
            this.name = validatePlayerName(name);
            return this;
        }

        @Override
        public IPlayerBuilder withRandomName()
        {
            this.uuid = UUID.randomUUID();
            this.name = "lk_bot_" + uuid.toString().substring(0, 8);
            return this;
        }

        @Override
        public IPlayerBuilder inWorld(WorldHandle world)
        {
            this.worldHandle = Objects.requireNonNull(world, "world may not be null.");
            this.x = null;
            this.y = null;
            this.z = null;
            return this;
        }

        @Override
        public IPlayerBuilder atLocation(WorldHandle world, double x, double y, double z)
        {
            this.worldHandle = Objects.requireNonNull(world, "world may not be null.");
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public IPlayerBuilder atSpawn(WorldHandle world)
        {
            this.worldHandle = Objects.requireNonNull(world, "world may not be null.");
            this.x = null;
            this.y = null;
            this.z = null;
            return this;
        }

        @Override
        public IPlayerBuilder withHealth(double health)
        {
            if (health <= 0.0D)
                throw new IllegalArgumentException("health must be > 0.");
            this.health = health;
            return this;
        }

        @Override
        public IPlayerBuilder withPermissions(String... permissions)
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

            final AgentPlayerData createdPlayer = agentClient.createPlayer(
                validatePlayerName(name),
                uuid,
                effectiveWorldHandle.name(),
                x,
                y,
                z,
                health,
                permissions
            );
            playerScopeRegistry.register(createdPlayer.uniqueId());
            return new PlayerHandle(DefaultLightkeeperFramework.this, createdPlayer.uniqueId(), createdPlayer.name());
        }
    }
}
