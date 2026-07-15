package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import nl.pim16aap2.lightkeeper.framework.Condition;
import nl.pim16aap2.lightkeeper.framework.EntitySnapshot;
import nl.pim16aap2.lightkeeper.framework.IBots;
import nl.pim16aap2.lightkeeper.framework.IEvents;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.IServerControl;
import nl.pim16aap2.lightkeeper.framework.IWorlds;
import nl.pim16aap2.lightkeeper.framework.InventorySnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import nl.pim16aap2.lightkeeper.framework.Vec3;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.DropResult;
import nl.pim16aap2.lightkeeper.protocol.GetServerErrors;
import nl.pim16aap2.lightkeeper.protocol.MutatePlayerPermission;
import nl.pim16aap2.lightkeeper.protocol.QueryEntities;
import nl.pim16aap2.lightkeeper.protocol.ServerErrorEntry;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestReader;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestValidator;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default LightKeeper framework implementation.
 *
 * <p>Public API surface is delegated to the facet facades ({@link ServerControlFacade}, {@link WorldsFacade},
 * {@link BotsFacade}, {@link EventsFacade}); this class owns the shared runtime internals and the lifecycle/close
 * plumbing, and implements the internal {@link IFrameworkGateway} seam consumed by handles.
 */
public final class DefaultLightkeeperFramework implements ILightkeeperFramework, IFrameworkGateway
{
    private static final System.Logger LOG = System.getLogger(DefaultLightkeeperFramework.class.getName());

    static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    static final Duration AGENT_CONNECT_TIMEOUT = Duration.ofSeconds(45);
    static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(45);
    private static final String DEFAULT_WORLD_NAME_PREFIX = "lk_world_";
    static final WorldSpec.WorldType DEFAULT_WORLD_TYPE = WorldSpec.WorldType.NORMAL;
    static final WorldSpec.WorldEnvironment DEFAULT_WORLD_ENVIRONMENT = WorldSpec.WorldEnvironment.NORMAL;
    static final long DEFAULT_WORLD_SEED = 0L;

    private final RuntimeManifest runtimeManifest;
    private final MinecraftServerProcess minecraftServerProcess;
    private final UdsAgentClient agentClient;
    private final PlayerScopeRegistry playerScopeRegistry;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /**
     * Whether the server was taken down — via {@link IServerControl#crash()} or {@link IServerControl#stop()} — and
     * has not been started again yet.
     */
    private final AtomicBoolean serverDown = new AtomicBoolean(false);
    /**
     * Total-line-count watermark delimiting the raw stderr scan window; advanced on every
     * {@link #clearServerErrors()}.
     */
    private final AtomicLong stderrScanWatermark = new AtomicLong(0L);

    private final ServerControlFacade serverControlFacade;
    private final WorldsFacade worldsFacade;
    private final BotsFacade botsFacade;
    private final EventsFacade eventsFacade;

    @Inject
    DefaultLightkeeperFramework(
        RuntimeManifest runtimeManifest,
        MinecraftServerProcess minecraftServerProcess,
        UdsAgentClient agentClient,
        PlayerScopeRegistry playerScopeRegistry)
    {
        this.runtimeManifest = Objects.requireNonNull(runtimeManifest, "runtimeManifest may not be null.");
        this.minecraftServerProcess =
            Objects.requireNonNull(minecraftServerProcess, "minecraftServerProcess may not be null.");
        this.agentClient = Objects.requireNonNull(agentClient, "agentClient may not be null.");
        this.playerScopeRegistry = Objects.requireNonNull(playerScopeRegistry, "playerScopeRegistry may not be null.");

        this.serverControlFacade = new ServerControlFacade(
            this, runtimeManifest, minecraftServerProcess, agentClient, playerScopeRegistry);
        this.worldsFacade = new WorldsFacade(this, runtimeManifest, agentClient);
        this.botsFacade = new BotsFacade(this, agentClient, playerScopeRegistry);
        this.eventsFacade = new EventsFacade(this, agentClient);
    }

    /**
     * Starts a framework from a runtime manifest path.
     *
     * @param runtimeManifestPath
     *     Path to runtime manifest.
     * @return Started framework.
      * <p>
     * Fails loudly (rather than silently degrading to the stderr net alone) when the agent-side capture
     * never attached: a disabled safety net must fail the tests that rely on it, not weaken them.
     */
    public static DefaultLightkeeperFramework start(Path runtimeManifestPath)
    {
        final RuntimeManifest runtimeManifest = readRuntimeManifest(runtimeManifestPath);
        RuntimeManifestValidator.validateForRuntimeStartup(runtimeManifest, RuntimeProtocol.VERSION);

        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Path diagnosticsDirectory = serverDirectory.resolveSibling("lightkeeper-diagnostics");
        final MinecraftServerProcess minecraftServerProcess =
            new MinecraftServerProcess(runtimeManifest, diagnosticsDirectory);

        UdsAgentClient agentClient = null;
        try
        {
            LOG.log(
                System.Logger.Level.INFO,
                () -> "LK_FRAMEWORK: Starting Minecraft server from '" + serverDirectory + "'."
            );
            minecraftServerProcess.start(STARTUP_TIMEOUT);

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
                minecraftServerProcess,
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
            minecraftServerProcess.stop(SHUTDOWN_TIMEOUT);
            throw exception;
        }
    }

    @Override
    public IServerControl server()
    {
        return serverControlFacade;
    }

    @Override
    public IWorlds worlds()
    {
        return worldsFacade;
    }

    @Override
    public IBots bots()
    {
        return botsFacade;
    }

    @Override
    public IEvents events()
    {
        return eventsFacade;
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
    public String getBlock(String worldName, BlockPos position)
    {
        ensureOpen();
        Objects.requireNonNull(worldName, "worldName may not be null.");
        Objects.requireNonNull(position, "position may not be null.");
        return agentClient.blockType(worldName, position);
    }

    @Override
    public void setBlock(String worldName, BlockPos position, String material)
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
    public String getBlockData(String worldName, BlockPos position)
    {
        ensureOpen();
        Objects.requireNonNull(worldName, "worldName may not be null.");
        Objects.requireNonNull(position, "position may not be null.");
        return agentClient.blockData(worldName, position);
    }

    @Override
    public void setBlockData(String worldName, BlockPos position, String blockData)
    {
        ensureOpen();
        Objects.requireNonNull(worldName, "worldName may not be null.");
        Objects.requireNonNull(position, "position may not be null.");
        final String trimmedBlockData = Objects.requireNonNull(blockData, "blockData may not be null.").trim();
        if (trimmedBlockData.isEmpty())
            throw new IllegalArgumentException("blockData may not be blank.");
        agentClient.setBlockData(worldName, position, trimmedBlockData);
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
    public void grantPermission(UUID playerId, String permission)
    {
        mutatePermission(playerId, permission, MutatePlayerPermission.Mode.GRANT);
    }

    @Override
    public void revokePermission(UUID playerId, String permission)
    {
        mutatePermission(playerId, permission, MutatePlayerPermission.Mode.REVOKE);
    }

    @Override
    public void unsetPermission(UUID playerId, String permission)
    {
        mutatePermission(playerId, permission, MutatePlayerPermission.Mode.UNSET);
    }

    @Override
    public boolean hasPermission(UUID playerId, String permission)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        return agentClient.hasPlayerPermission(playerId, validatePermission(permission));
    }

    private void mutatePermission(UUID playerId, String permission, MutatePlayerPermission.Mode mode)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        agentClient.mutatePlayerPermission(playerId, validatePermission(permission), mode);
    }

    private static String validatePermission(String permission)
    {
        final String trimmedPermission = Objects.requireNonNull(permission, "permission may not be null.").trim();
        if (trimmedPermission.isEmpty())
            throw new IllegalArgumentException("permission may not be blank.");
        return trimmedPermission;
    }

    @Override
    public boolean teleportPlayer(UUID uuid, String worldName, double x, double y, double z)
    {
        ensureOpen();
        Objects.requireNonNull(uuid, "uuid may not be null.");
        Objects.requireNonNull(worldName, "worldName may not be null.");
        return agentClient.teleportPlayer(uuid, worldName, x, y, z);
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
    public boolean loadChunk(String worldName, int x, int z)
    {
        ensureOpen();
        Objects.requireNonNull(worldName, "worldName may not be null.");
        return agentClient.loadChunk(worldName, x, z);
    }

    @Override
    public boolean unloadChunk(String worldName, int x, int z)
    {
        ensureOpen();
        Objects.requireNonNull(worldName, "worldName may not be null.");
        return agentClient.unloadChunk(worldName, x, z);
    }

    @Override
    public boolean isChunkLoaded(String worldName, int x, int z)
    {
        ensureOpen();
        Objects.requireNonNull(worldName, "worldName may not be null.");
        return agentClient.isChunkLoaded(worldName, x, z);
    }

    @Override
    public boolean leftClickBlock(UUID playerId, BlockPos position, String blockFace)
    {
        return clickBlock(playerId, position, blockFace, agentClient::leftClickBlock);
    }

    @Override
    public boolean rightClickBlock(UUID playerId, BlockPos position, String blockFace)
    {
        return clickBlock(playerId, position, blockFace, agentClient::rightClickBlock);
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
        if (slots.length == 0)
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
        LOG.log(
            System.Logger.Level.INFO,
            () -> "LK_FRAMEWORK: Removed player '%s' (%s)."
                .formatted(player.name(), player.uniqueId())
        );
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
    public List<ChatComponentSnapshot> playerChatComponents(UUID playerId)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        return agentClient.playerChatComponents(playerId);
    }

    @Override
    public InventorySnapshot playerInventory(UUID playerId)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        return InventorySnapshot.fromItems(agentClient.getPlayerInventory(playerId));
    }

    @Override
    public DropResult dropItem(UUID playerId)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        return agentClient.dropItem(playerId);
    }

    @Override
    public void registerEventListener(String eventClassName)
    {
        ensureOpen();
        agentClient.registerEventListener(eventClassName);
    }

    @Override
    public List<CapturedEventSnapshot> getCapturedEvents(String eventClassName)
    {
        ensureOpen();
        return agentClient.getCapturedEvents(eventClassName).stream()
            .map(event -> new CapturedEventSnapshot(eventClassName, event.tick(), event.values()))
            .toList();
    }

    @Override
    public void cancelNextEvents(String eventClassName, int count)
    {
        ensureOpen();
        Objects.requireNonNull(eventClassName, "eventClassName may not be null.");
        if (count <= 0)
            throw new IllegalArgumentException("count must be positive.");
        agentClient.cancelNextEvents(eventClassName, count);
    }

    @Override
    public void playerChat(UUID playerId, String message)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        Objects.requireNonNull(message, "message may not be null.");
        if (message.isBlank())
            throw new IllegalArgumentException("message may not be blank.");
        // Deliberately NOT trimmed: intentional whitespace must reach the fired chat event unchanged.
        agentClient.playerChat(playerId, message);
    }

    @Override
    public long currentServerTick()
    {
        ensureOpen();
        return agentClient.getServerTick();
    }

    @Override
    public int countEntities(
        String worldName,
        @Nullable String entityTypeKey,
        @Nullable BlockPos boundsMin,
        @Nullable BlockPos boundsMax)
    {
        ensureOpen();
        Objects.requireNonNull(worldName, "worldName may not be null.");
        return agentClient.queryEntities(worldName, entityTypeKey, boundsMin, boundsMax, true).count();
    }

    @Override
    public List<EntitySnapshot> snapshotEntities(
        String worldName,
        @Nullable String entityTypeKey,
        @Nullable BlockPos boundsMin,
        @Nullable BlockPos boundsMax)
    {
        ensureOpen();
        Objects.requireNonNull(worldName, "worldName may not be null.");
        final QueryEntities.Response response =
            agentClient.queryEntities(worldName, entityTypeKey, boundsMin, boundsMax, false);
        return response.entities().stream()
            .map(entity -> toEntitySnapshot(entity, response.tick()))
            .toList();
    }

    private static EntitySnapshot toEntitySnapshot(QueryEntities.EntityData entity, long tick)
    {
        return new EntitySnapshot(
            entity.uuid(),
            entity.typeKey(),
            new Vec3(entity.x(), entity.y(), entity.z()),
            entity.customName(),
            entity.pdcKeys(),
            entity.transform() == null ? null : toTransform(entity.transform()),
            tick
        );
    }

    private static EntitySnapshot.Transform toTransform(QueryEntities.TransformData transform)
    {
        return new EntitySnapshot.Transform(
            new Vec3(transform.translationX(), transform.translationY(), transform.translationZ()),
            new Vec3(transform.scaleX(), transform.scaleY(), transform.scaleZ()),
            toRotation(transform.leftRotation()),
            toRotation(transform.rightRotation())
        );
    }

    private static EntitySnapshot.Rotation toRotation(List<Double> quaternion)
    {
        if (quaternion.size() != 4)
            throw new IllegalStateException(
                "Expected a 4-component rotation quaternion but received %d components."
                    .formatted(quaternion.size()));
        return new EntitySnapshot.Rotation(
            quaternion.get(0), quaternion.get(1), quaternion.get(2), quaternion.get(3));
    }

    @Override
    public void clearCapturedEvents(String eventClassName)
    {
        ensureOpen();
        agentClient.clearCapturedEvents(eventClassName);
    }

    @Override
    public void unregisterEventListener(String eventClassName)
    {
        ensureOpen();
        agentClient.unregisterEventListener(eventClassName);
    }

    @Override
    public List<ServerErrorSnapshot> capturedServerErrors()
    {
        ensureOpen();
        final GetServerErrors.Response response = agentClient.getServerErrors();
        if (!response.captureActive())
            throw new IllegalStateException(
                "Structured server-error capture is inactive: the agent could not attach its log appender. "
                    + "Captured server errors are unavailable on this server.");
        if (response.droppedCount() > 0L)
            LOG.log(
                System.Logger.Level.WARNING,
                () -> "LK_FRAMEWORK: Server-error capture dropped %d entries because its buffer was full."
                    .formatted(response.droppedCount())
            );

        final List<ServerErrorSnapshot> snapshots = new ArrayList<>(response.errors().size());
        for (final ServerErrorEntry entry : response.errors())
            snapshots.add(toServerErrorSnapshot(entry));
        snapshots.addAll(ServerStderrErrorScanner.scan(
            minecraftServerProcess.snapshotStderrLinesFrom(stderrScanWatermark.get())));
        return List.copyOf(snapshots);
    }

    @Override
    public void clearServerErrors()
    {
        ensureOpen();
        // Snapshot the watermark before the RPC: stderr lines written during the round trip must
        // stay above the watermark so they still surface in later capturedServerErrors() calls.
        final long watermark = minecraftServerProcess.totalOutputLineCount();
        agentClient.clearServerErrors();
        stderrScanWatermark.set(watermark);
    }

    private static ServerErrorSnapshot toServerErrorSnapshot(ServerErrorEntry entry)
    {
        return new ServerErrorSnapshot(
            entry.timestampMillis(),
            "ERROR".equals(entry.severity())
                ? ServerErrorSnapshot.Severity.ERROR
                : ServerErrorSnapshot.Severity.WARNING,
            entry.levelName(),
            entry.loggerName(),
            entry.threadName(),
            entry.message(),
            entry.throwableClass(),
            entry.throwableMessage(),
            entry.stackTrace()
        );
    }

    private boolean clickBlock(UUID playerId, BlockPos position, String blockFace, BlockClickOperation operation)
    {
        ensureOpen();
        Objects.requireNonNull(playerId, "playerId may not be null.");
        Objects.requireNonNull(position, "position may not be null.");
        final String trimmedBlockFace = Objects.requireNonNull(blockFace, "blockFace may not be null.").trim();
        if (trimmedBlockFace.isEmpty())
            throw new IllegalArgumentException("blockFace may not be blank.");
        return operation.clickBlock(playerId, position, trimmedBlockFace);
    }

    @FunctionalInterface
    private interface BlockClickOperation
    {
        boolean clickBlock(UUID playerId, BlockPos position, String blockFace);
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
            return;

        try
        {
            LOG.log(
                System.Logger.Level.INFO,
                "LK_FRAMEWORK: Closing framework and cleaning up players."
            );
            playerScopeRegistry.cleanupAll(agentClient::removePlayer);
            agentClient.close();
        }
        finally
        {
            LOG.log(
                System.Logger.Level.INFO,
                "LK_FRAMEWORK: Stopping Minecraft server."
            );
            minecraftServerProcess.stop(SHUTDOWN_TIMEOUT);
        }
    }

    public void beginMethodScope(String methodExecutionId)
    {
        ensureOpen();
        if (serverDown.get())
            throw new IllegalStateException(
                "A previous test took down the shared Minecraft server via crashServer() or stopServer() "
                    + "without starting it again. Call startServer() (or restartServer()) before the test ends, "
                    + "or annotate the test with @FreshServer so each method receives a fresh server.");
        playerScopeRegistry.beginMethodScope(methodExecutionId);
    }

    public void endMethodScope(String methodExecutionId)
    {
        ensureOpen();
        playerScopeRegistry.endMethodScope(methodExecutionId, agentClient::removePlayer);
        // Clear captured server errors at the END of each method (not the start): boot-window errors stay
        // visible to the first test's assertions, and every later test only observes its own window. Skipped
        // while the server is down (crashed or stopped), when the agent connection is gone.
        if (!serverDown.get())
            clearServerErrors();
    }

    /**
     * Preloads the worlds configured in the runtime manifest with {@code loadOnStartup=true}.
     *
     * <p>Shared by the initial boot path ({@link #start(Path)}) and the server-control restart path
     * ({@link IServerControl#start()}).
     */
    void preloadConfiguredWorlds()
    {
        for (final RuntimeManifest.ProvisionedWorld provisionedWorld : runtimeManifest.provisionedWorlds())
        {
            if (!provisionedWorld.loadOnStartup())
                continue;
            final String worldName = agentClient.newWorld(toWorldSpec(provisionedWorld));
            LOG.log(
                System.Logger.Level.INFO,
                () -> "LK_FRAMEWORK: Preloaded world '%s' from runtime manifest.".formatted(worldName)
            );
        }
    }

    /**
     * Marks the shared Minecraft server as taken down (crashed or stopped) and not yet restarted.
     */
    void markServerDown()
    {
        serverDown.set(true);
    }

    /**
     * Marks the shared Minecraft server as running again after a successful start.
     */
    void markServerUp()
    {
        serverDown.set(false);
    }

    void ensureOpen()
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

    static WorldSpec toWorldSpec(RuntimeManifest.ProvisionedWorld provisionedWorld)
    {
        return new WorldSpec(
            provisionedWorld.name(),
            WorldSpec.WorldType.valueOf(provisionedWorld.worldType()),
            WorldSpec.WorldEnvironment.valueOf(provisionedWorld.environment()),
            provisionedWorld.seed()
        );
    }

    static String createDefaultWorldName()
    {
        return DEFAULT_WORLD_NAME_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    static String validatePlayerName(String name)
    {
        final String trimmedName = Objects.requireNonNull(name, "name may not be null.").trim();
        if (trimmedName.isEmpty())
            throw new IllegalArgumentException("name may not be blank.");
        if (trimmedName.length() > 16)
            throw new IllegalArgumentException("name may not exceed 16 characters.");
        return trimmedName;
    }

    WorldHandle createWorldFromBuilder(WorldSpec worldSpec)
    {
        return worldsFacade.create(worldSpec);
    }

    PlayerHandle createPlayerFromBuilder(
        String name,
        UUID uuid,
        WorldHandle worldHandle,
        @Nullable Double x,
        @Nullable Double y,
        @Nullable Double z,
        @Nullable Double health,
        java.util.Set<String> permissions)
    {
        return botsFacade.createFromBuilder(name, uuid, worldHandle, x, y, z, health, permissions);
    }
}
