package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import nl.pim16aap2.lightkeeper.protocol.BlockType;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommand;
import nl.pim16aap2.lightkeeper.protocol.GetServerPlatform;
import nl.pim16aap2.lightkeeper.protocol.GetServerTick;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoaded;
import nl.pim16aap2.lightkeeper.protocol.LoadChunk;
import nl.pim16aap2.lightkeeper.protocol.MainWorld;
import nl.pim16aap2.lightkeeper.protocol.NewWorld;
import nl.pim16aap2.lightkeeper.protocol.SetBlock;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunk;
import nl.pim16aap2.lightkeeper.protocol.WaitTicks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.java.JavaPlugin;

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
        return new MainWorld.Response(command.requestId(), mainWorld.getName());
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
        return new NewWorld.Response(command.requestId(), world.getName());
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
    ExecuteCommand.Response handleExecuteCommand(ExecuteCommand.Command req)
        throws Exception
    {
        if (req.commandSource() != CommandSource.CONSOLE)
            throw new IllegalArgumentException("Only CONSOLE command source is supported in v1.");

        final String rawCommand = req.command();
        final String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean success = mainThreadExecutor.callOnMainThread(() ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        );

        return new ExecuteCommand.Response(req.requestId(), success);
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

        final String materialName = mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            return world.getBlockAt(x, y, z).getType().name();
        });

        return new BlockType.Response(command.requestId(), materialName);
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

        final Material material = AgentRequestParsers.parseMaterial(materialKey);
        if (material == null)
            throw new IllegalArgumentException("Unknown material '%s'.".formatted(materialKey));

        final String setMaterial = mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            world.getBlockAt(x, y, z).setType(material);
            return world.getBlockAt(x, y, z).getType().name();
        });

        return new SetBlock.Response(command.requestId(), setMaterial);
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

        return new WaitTicks.Response(command.requestId(), startTick, tickCounter.get());
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
        return new GetServerTick.Response(command.requestId(), tickCounter.get());
    }

    /**
     * Handles {@code LOAD_CHUNK} by force-loading the chunk at the given chunk coordinates.
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

        return new LoadChunk.Response(command.requestId(), loaded);
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

        return new UnloadChunk.Response(command.requestId(), unloaded);
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

        return new IsChunkLoaded.Response(command.requestId(), loaded);
    }

    /**
     * Handles {@code GET_SERVER_PLATFORM} by returning the server implementation name and version.
     *
     * @param command
     *     Typed command carrying the request identifier.
     * @return
     *     Response containing {@code serverName} and {@code serverVersion}.
     */
    GetServerPlatform.Response handleGetServerPlatform(GetServerPlatform.Command command)
    {
        return new GetServerPlatform.Response(command.requestId(), Bukkit.getName(), Bukkit.getVersion());
    }
}
