package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import nl.pim16aap2.lightkeeper.protocol.BlockType;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommand;
import nl.pim16aap2.lightkeeper.protocol.GetServerPlatform;
import nl.pim16aap2.lightkeeper.protocol.GetServerTick;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoaded;
import nl.pim16aap2.lightkeeper.protocol.LoadChunk;
import nl.pim16aap2.lightkeeper.protocol.MainWorld;
import nl.pim16aap2.lightkeeper.protocol.NewWorld;
import nl.pim16aap2.lightkeeper.protocol.QueryEntities;
import nl.pim16aap2.lightkeeper.protocol.SetBlock;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunk;
import nl.pim16aap2.lightkeeper.protocol.WaitTicks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Protocol action handler for world-level and server-level operations.
 *
 * <p>This handler covers world discovery/creation, console command execution, block reads/writes, and server
 * tick synchronization features used by runtime integration tests.
 */
final class AgentWorldActions
{
    /**
     * Maximum wall-clock time for {@code WAIT_TICKS} polling.
     */
    private static final long WAIT_TICKS_TIMEOUT_MILLIS = 60_000L;

    /**
     * Owning plugin used for logging.
     */
    private final JavaPlugin plugin;
    /**
     * Scheduler bridge for Bukkit main-thread execution.
     */
    private final AgentMainThreadExecutor mainThreadExecutor;
    /**
     * Monotonic tick counter incremented by the plugin scheduler loop.
     */
    private final AtomicLong tickCounter;

    /**
     * @param plugin
     *     Plugin context used for logging.
     * @param mainThreadExecutor
     *     Main-thread execution bridge for Bukkit-safe operations.
     * @param tickCounter
     *     Shared tick counter maintained by the plugin tick loop.
     */
    AgentWorldActions(JavaPlugin plugin, AgentMainThreadExecutor mainThreadExecutor, AtomicLong tickCounter)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.tickCounter = Objects.requireNonNull(tickCounter, "tickCounter");
    }

    /**
     * Increments the shared server tick counter by one.
     */
    void incrementTick()
    {
        tickCounter.incrementAndGet();
    }

    /**
     * Handles {@code MAIN_WORLD} by returning the name of Bukkit's first loaded world.
     *
     * @param command
     *     Typed command carrying the request identifier.
     * @return
     *     Response containing {@code worldName}.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    MainWorld.Response handleMainWorld(MainWorld.Command command)
        throws Exception
    {
        final World mainWorld = mainThreadExecutor.callOnMainThread(() -> Bukkit.getWorlds().getFirst());
        return new MainWorld.Response(mainWorld.getName());
    }

    /**
     * Handles {@code NEW_WORLD} by creating or loading a world with deterministic creator settings.
     *
     * @param command
     *     Typed command carrying world name, type, environment, and seed.
     * @return
     *     Response containing resolved world name.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    NewWorld.Response handleNewWorld(NewWorld.Command command)
        throws Exception
    {
        final String worldName = command.worldName();
        final String worldTypeValue = command.worldType();
        final String environmentValue = command.environment();
        final long seed = command.seed();

        if (worldName.isBlank())
            throw new IllegalArgumentException("Argument 'worldName' must not be blank.");

        final World world = mainThreadExecutor.callOnMainThread(() ->
        {
            final WorldCreator worldCreator = new WorldCreator(worldName);
            worldCreator.type(WorldType.valueOf(worldTypeValue.toUpperCase(Locale.ROOT)));
            worldCreator.environment(World.Environment.valueOf(environmentValue.toUpperCase(Locale.ROOT)));
            worldCreator.seed(seed);
            return Objects.requireNonNull(worldCreator.createWorld());
        });

        plugin.getLogger().info(
            "LK_AGENT: Created/loaded world '%s' (type=%s, environment=%s, seed=%d)."
                .formatted(world.getName(), worldTypeValue, environmentValue, seed)
        );
        return new NewWorld.Response(world.getName());
    }

    /**
     * Handles {@code EXECUTE_COMMAND} by running a console command.
     *
     * @param command
     *     Typed command carrying command source and command string.
     * @return
     *     Response with command execution result.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    ExecuteCommand.Response handleExecuteCommand(ExecuteCommand.Command command)
        throws Exception
    {
        final String rawCommand = command.command();
        if (rawCommand.isBlank())
            throw new IllegalArgumentException("Argument 'command' must not be blank.");

        final String normalizedCommand = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean dispatched = mainThreadExecutor.callOnMainThread(() ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalizedCommand)
        );

        return new ExecuteCommand.Response(dispatched);
    }

    /**
     * Handles {@code BLOCK_TYPE} by returning the material at target coordinates.
     *
     * @param command
     *     Typed command carrying world name and block coordinates.
     * @return
     *     Response containing the resolved block material.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    BlockType.Response handleBlockType(BlockType.Command command)
        throws Exception
    {
        final String worldName = command.worldName();
        final int x = command.x();
        final int y = command.y();
        final int z = command.z();

        return mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            final var block = world.getBlockAt(x, y, z);
            return new BlockType.Response(
                block.getType().getKey().toString(),
                block.getBlockData().getAsString()
            );
        });
    }

    /**
     * Handles {@code SET_BLOCK} by setting a block to the requested material.
     *
     * @param command
     *     Typed command carrying world name, block coordinates, and material key.
     * @return
     *     Response containing resulting material name.
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    SetBlock.Response handleSetBlock(SetBlock.Command command)
        throws Exception
    {
        final String worldName = command.worldName();
        final int x = command.x();
        final int y = command.y();
        final int z = command.z();
        final String materialKey = command.materialKey();
        final String blockData = command.blockData();

        final Material material = blockData != null ? null : AgentRequestParsers.parseMaterial(materialKey);
        if (blockData == null && material == null)
            throw new IllegalArgumentException("Unknown material '%s'.".formatted(materialKey));

        final String setMaterial = mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            final var block = world.getBlockAt(x, y, z);
            if (blockData != null)
                // Throws IllegalArgumentException on malformed input, surfacing as INVALID_ARGUMENT.
                block.setBlockData(Bukkit.createBlockData(blockData));
            else
                block.setType(material);
            return block.getType().name();
        });

        return new SetBlock.Response(setMaterial);
    }

    /**
     * Handles {@code QUERY_ENTITIES} by reading all matching entities in one main-thread burst, so the
     * returned states are internally consistent and share one tick stamp.
     *
     * <p>Bounded queries match by hitbox intersection with the block-inclusive box (each max axis is
     * extended by one so block coordinates span their full world-space cell), per the Bukkit
     * nearby-entities semantic — not by position containment.
     *
     * @param command
     *     Typed command carrying world name, optional type filter, optional bounds, and the count-only flag.
     * @return
     *     Response with the match count and (unless count-only) the entity states.
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    QueryEntities.Response handleQueryEntities(QueryEntities.Command command)
        throws Exception
    {
        final String worldName = command.worldName();
        final String entityTypeKey = command.entityTypeKey() == null
            ? null
            : normalizeEntityTypeKey(command.entityTypeKey());

        return mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));

            final Collection<Entity> candidates = command.bounded()
                ? world.getNearbyEntities(new BoundingBox(
                    command.minX(), command.minY(), command.minZ(),
                    command.maxX() + 1.0, command.maxY() + 1.0, command.maxZ() + 1.0))
                : world.getEntities();

            final List<QueryEntities.EntityData> matches = new ArrayList<>();
            int count = 0;
            for (final Entity entity : candidates)
            {
                // getKey(), not getKeyOrThrow(): the latter exists only in the Spigot API jar; Paper's
                // EntityType lacks it at runtime and the agent must run on both.
                if (entityTypeKey != null
                    && !entity.getType().getKey().toString().equals(entityTypeKey))
                    continue;
                count++;
                if (!command.countOnly())
                    matches.add(toEntityData(entity));
            }
            return new QueryEntities.Response(tickCounter.get(), count, matches);
        });
    }

    private static String normalizeEntityTypeKey(String entityTypeKey)
    {
        final String trimmed = entityTypeKey.trim().toLowerCase(Locale.ROOT);
        return trimmed.indexOf(':') < 0 ? "minecraft:" + trimmed : trimmed;
    }

    private static QueryEntities.EntityData toEntityData(Entity entity)
    {
        final Location location = entity.getLocation();
        final List<String> pdcKeys = entity.getPersistentDataContainer().getKeys().stream()
            .map(Object::toString)
            .sorted()
            .toList();
        return new QueryEntities.EntityData(
            entity.getUniqueId(),
            entity.getType().getKey().toString(),
            location.getX(),
            location.getY(),
            location.getZ(),
            entity.getCustomName(),
            pdcKeys,
            entity instanceof Display display ? toTransformData(display) : null
        );
    }

    private static QueryEntities.TransformData toTransformData(Display display)
    {
        final Transformation transformation = display.getTransformation();
        final Vector3f translation = transformation.getTranslation();
        final Vector3f scale = transformation.getScale();
        final Quaternionf leftRotation = transformation.getLeftRotation();
        final Quaternionf rightRotation = transformation.getRightRotation();
        return new QueryEntities.TransformData(
            translation.x(), translation.y(), translation.z(),
            scale.x(), scale.y(), scale.z(),
            List.of((double) leftRotation.x(), (double) leftRotation.y(),
                (double) leftRotation.z(), (double) leftRotation.w()),
            List.of((double) rightRotation.x(), (double) rightRotation.y(),
                (double) rightRotation.z(), (double) rightRotation.w())
        );
    }

    /**
     * Handles {@code WAIT_TICKS} by polling until the tick counter reaches the target value.
     *
     * @param command
     *     Typed command carrying the number of ticks to wait.
     * @return
     *     Response with start/end tick values.
     * @throws AgentProtocolException
     *     On invalid arguments, timeout, or interruption.
     */
    WaitTicks.Response handleWaitTicks(WaitTicks.Command command)
    {
        final int ticks = command.ticks();
        final long startTick = tickCounter.get();
        final long targetTick = startTick + ticks;
        final long deadline = System.currentTimeMillis() + WAIT_TICKS_TIMEOUT_MILLIS;
        while (tickCounter.get() < targetTick)
        {
            if (System.currentTimeMillis() >= deadline)
                throw new AgentProtocolException(
                    AgentErrorCode.TIMEOUT,
                    "Timed out waiting for %d ticks. start=%d current=%d target=%d"
                        .formatted(ticks, startTick, tickCounter.get(), targetTick)
                );

            try
            {
                //noinspection BusyWait
                Thread.sleep(10L);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                throw new AgentProtocolException(
                    AgentErrorCode.INTERRUPTED,
                    "Interrupted while waiting for ticks.",
                    exception
                );
            }
        }

        return new WaitTicks.Response(startTick, tickCounter.get());
    }

    /**
     * Handles {@code GET_SERVER_TICK}.
     *
     * @param command
     *     Typed command carrying the request identifier.
     * @return
     *     Response with current tick counter value.
     */
    GetServerTick.Response handleGetServerTick(GetServerTick.Command command)
    {
        return new GetServerTick.Response(tickCounter.get());
    }

    /**
     * Handles {@code LOAD_CHUNK} by loading (and generating if absent) the chunk at the given chunk coordinates.
     *
     * @param command
     *     Typed command carrying world name and chunk coordinates.
     * @return
     *     Response with whether the chunk was loaded.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    LoadChunk.Response handleLoadChunk(LoadChunk.Command command)
        throws Exception
    {
        final String worldName = command.worldName();
        final int x = command.x();
        final int z = command.z();

        final Boolean loaded = mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            return world.loadChunk(x, z, true);
        });

        return new LoadChunk.Response(loaded);
    }

    /**
     * Handles {@code UNLOAD_CHUNK} by unloading the chunk at the given chunk coordinates.
     *
     * @param command
     *     Typed command carrying world name and chunk coordinates.
     * @return
     *     Response with whether the chunk was unloaded.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    UnloadChunk.Response handleUnloadChunk(UnloadChunk.Command command)
        throws Exception
    {
        final String worldName = command.worldName();
        final int x = command.x();
        final int z = command.z();

        final Boolean unloaded = mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            return world.unloadChunk(x, z);
        });

        return new UnloadChunk.Response(unloaded);
    }

    /**
     * Handles {@code IS_CHUNK_LOADED} by querying whether the chunk is currently loaded.
     *
     * @param command
     *     Typed command carrying world name and chunk coordinates.
     * @return
     *     Response with {@code loaded} boolean.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    IsChunkLoaded.Response handleIsChunkLoaded(IsChunkLoaded.Command command)
        throws Exception
    {
        final String worldName = command.worldName();
        final int x = command.x();
        final int z = command.z();

        final Boolean loaded = mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            return world.isChunkLoaded(x, z);
        });

        return new IsChunkLoaded.Response(loaded);
    }

    /**
     * Handles {@code GET_SERVER_PLATFORM} by classifying the running server as PAPER, SPIGOT, or UNKNOWN.
     *
     * @param command
     *     Typed command carrying the request identifier.
     * @return
     *     Response containing the canonical platform identifier.
     */
    GetServerPlatform.Response handleGetServerPlatform(GetServerPlatform.Command command)
    {
        return new GetServerPlatform.Response(AgentPlatformDetector.detect());
    }
}
