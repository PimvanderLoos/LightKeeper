package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.BlockTypeCommand;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommandCommand;
import nl.pim16aap2.lightkeeper.protocol.GetServerPlatformCommand;
import nl.pim16aap2.lightkeeper.protocol.GetServerTickCommand;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoadedCommand;
import nl.pim16aap2.lightkeeper.protocol.LoadChunkCommand;
import nl.pim16aap2.lightkeeper.protocol.MainWorldCommand;
import nl.pim16aap2.lightkeeper.protocol.NewWorldCommand;
import nl.pim16aap2.lightkeeper.protocol.SetBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunkCommand;
import nl.pim16aap2.lightkeeper.protocol.WaitTicksCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
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
     *     Success response containing {@code worldName}.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleMainWorld(MainWorldCommand command)
        throws Exception
    {
        final World mainWorld = mainThreadExecutor.callOnMainThread(() -> Bukkit.getWorlds().getFirst());
        return AgentResponses.successResponse(command.requestId(), Map.of("worldName", mainWorld.getName()));
    }

    /**
     * Handles {@code NEW_WORLD} by creating or loading a world with deterministic creator settings.
     *
     * @param command
     *     Typed command carrying world name, type, environment, and seed.
     * @return
     *     Success response containing resolved world name.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleNewWorld(NewWorldCommand command)
        throws Exception
    {
        final String worldName = command.worldName();
        final String worldTypeValue = command.worldType();
        final String environmentValue = command.environment();
        final long seed = command.seed();

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
        return AgentResponses.successResponse(command.requestId(), Map.of("worldName", world.getName()));
    }

    /**
     * Handles {@code EXECUTE_COMMAND} by running a console command.
     *
     * @param command
     *     Typed command carrying command source and command string.
     * @return
     *     Success or validation error response with command execution result.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleExecuteCommand(ExecuteCommandCommand command)
        throws Exception
    {
        final String source = command.commandSource();
        final String rawCommand = command.command();

        if (!source.equalsIgnoreCase("CONSOLE"))
        {
            return AgentResponses.errorResponse(
                command.requestId(),
                AgentErrorCode.UNSUPPORTED_SOURCE,
                "Only CONSOLE command source is supported in v1."
            );
        }

        final String cmd = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean success = mainThreadExecutor.callOnMainThread(() ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
        );

        return AgentResponses.successResponse(command.requestId(), Map.of("success", success.toString()));
    }

    /**
     * Handles {@code BLOCK_TYPE} by returning the material at target coordinates.
     *
     * @param command
     *     Typed command carrying world name and block coordinates.
     * @return
     *     Success response containing the resolved block material.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleBlockType(BlockTypeCommand command)
        throws Exception
    {
        final String worldName = command.worldName();
        final int x = command.x();
        final int y = command.y();
        final int z = command.z();

        final String materialName = mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            return world.getBlockAt(x, y, z).getType().name();
        });

        return AgentResponses.successResponse(command.requestId(), Map.of("material", materialName));
    }

    /**
     * Handles {@code SET_BLOCK} by setting a block to the requested material.
     *
     * @param command
     *     Typed command carrying world name, block coordinates, and material key.
     * @return
     *     Success response containing resulting material name, or validation error response.
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    AgentResponse handleSetBlock(SetBlockCommand command)
        throws Exception
    {
        final String worldName = command.worldName();
        final int x = command.x();
        final int y = command.y();
        final int z = command.z();
        final String materialKey = command.materialKey();

        final Material material = AgentRequestParsers.parseMaterial(materialKey);
        if (material == null)
        {
            return AgentResponses.errorResponse(
                command.requestId(),
                AgentErrorCode.INVALID_ARGUMENT,
                "Unknown material '%s'.".formatted(materialKey)
            );
        }

        final String setMaterial = mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            world.getBlockAt(x, y, z).setType(material);
            return world.getBlockAt(x, y, z).getType().name();
        });

        return AgentResponses.successResponse(command.requestId(), Map.of("material", setMaterial));
    }

    /**
     * Handles {@code WAIT_TICKS} by polling until the tick counter reaches the target value.
     *
     * @param command
     *     Typed command carrying the number of ticks to wait.
     * @return
     *     Success response with start/end tick values, or timeout/interruption/validation error response.
     */
    AgentResponse handleWaitTicks(WaitTicksCommand command)
    {
        final int ticks = command.ticks();
        if (ticks < 0)
        {
            return AgentResponses.errorResponse(
                command.requestId(),
                AgentErrorCode.INVALID_ARGUMENT,
                "Argument 'ticks' must be >= 0."
            );
        }

        final long startTick = tickCounter.get();
        final long targetTick = startTick + ticks;
        final long deadline = System.currentTimeMillis() + WAIT_TICKS_TIMEOUT_MILLIS;
        while (tickCounter.get() < targetTick)
        {
            if (System.currentTimeMillis() >= deadline)
            {
                return AgentResponses.errorResponse(
                    command.requestId(),
                    AgentErrorCode.TIMEOUT,
                    "Timed out waiting for %d ticks. start=%d current=%d target=%d"
                        .formatted(ticks, startTick, tickCounter.get(), targetTick)
                );
            }

            try
            {
                //noinspection BusyWait
                Thread.sleep(10L);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                return AgentResponses.errorResponse(
                    command.requestId(),
                    AgentErrorCode.INTERRUPTED,
                    "Interrupted while waiting for ticks."
                );
            }
        }

        return AgentResponses.successResponse(command.requestId(), Map.of(
            "startTick", Long.toString(startTick),
            "endTick", Long.toString(tickCounter.get())
        ));
    }

    /**
     * Handles {@code GET_SERVER_TICK}.
     *
     * @param command
     *     Typed command carrying the request identifier.
     * @return
     *     Success response with current tick counter value.
     */
    AgentResponse handleGetServerTick(GetServerTickCommand command)
    {
        return AgentResponses.successResponse(command.requestId(), Map.of("tick", Long.toString(tickCounter.get())));
    }

    /**
     * Handles {@code LOAD_CHUNK} by force-loading the chunk at the given chunk coordinates.
     *
     * @param command
     *     Typed command carrying world name and chunk coordinates.
     * @return
     *     Success response, or error if the world does not exist.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleLoadChunk(LoadChunkCommand command)
        throws Exception
    {
        final String worldName = command.worldName();
        final int x = command.x();
        final int z = command.z();

        mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            world.loadChunk(x, z, true);
            return Boolean.TRUE;
        });

        return AgentResponses.successResponse(command.requestId(), Map.of("loaded", "true"));
    }

    /**
     * Handles {@code UNLOAD_CHUNK} by unloading the chunk at the given chunk coordinates.
     *
     * @param command
     *     Typed command carrying world name and chunk coordinates.
     * @return
     *     Success response, or error if the world does not exist.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleUnloadChunk(UnloadChunkCommand command)
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

        return AgentResponses.successResponse(command.requestId(), Map.of("unloaded", unloaded.toString()));
    }

    /**
     * Handles {@code IS_CHUNK_LOADED} by querying whether the chunk is currently loaded.
     *
     * @param command
     *     Typed command carrying world name and chunk coordinates.
     * @return
     *     Success response with {@code loaded} boolean, or error if the world does not exist.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleIsChunkLoaded(IsChunkLoadedCommand command)
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

        return AgentResponses.successResponse(command.requestId(), Map.of("loaded", loaded.toString()));
    }

    /**
     * Handles {@code GET_SERVER_PLATFORM} by returning the server implementation name and version.
     *
     * @param command
     *     Typed command carrying the request identifier.
     * @return
     *     Success response containing {@code serverName} and {@code serverVersion}.
     */
    AgentResponse handleGetServerPlatform(GetServerPlatformCommand command)
    {
        return AgentResponses.successResponse(command.requestId(), Map.of(
            "serverName", Bukkit.getName(),
            "serverVersion", Bukkit.getVersion()
        ));
    }
}
