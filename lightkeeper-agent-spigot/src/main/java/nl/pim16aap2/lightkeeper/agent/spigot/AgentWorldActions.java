package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.runtime.agent.AgentErrorCode;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
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
     * @param requestId
     *     Runtime request identifier.
     * @return
     *     Success response containing {@code worldName}.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleMainWorld(String requestId)
        throws Exception
    {
        final World mainWorld = mainThreadExecutor.callOnMainThread(() -> Bukkit.getWorlds().getFirst());
        return AgentResponses.successResponse(requestId, Map.of("worldName", mainWorld.getName()));
    }

    /**
     * Handles {@code NEW_WORLD} by creating or loading a world with deterministic creator settings.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; requires {@code worldName}. Optional type/environment/seed override defaults.
     * @return
     *     Success response containing resolved world name, or validation error response.
     * @throws Exception
     *     Propagates parsing and main-thread execution failures.
     */
    AgentResponse handleNewWorld(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String worldName = arguments.getOrDefault("worldName", "").trim();
        if (worldName.isBlank())
        {
            return AgentResponses.errorResponse(
                requestId,
                AgentErrorCode.INVALID_ARGUMENT,
                "Argument 'worldName' must not be blank."
            );
        }

        final String worldTypeValue = arguments.getOrDefault("worldType", "NORMAL");
        final String environmentValue = arguments.getOrDefault("environment", "NORMAL");
        final long seed = AgentRequestParsers.parseLong(arguments.getOrDefault("seed", "0"));

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
        return AgentResponses.successResponse(requestId, Map.of("worldName", world.getName()));
    }

    /**
     * Handles {@code EXECUTE_COMMAND} by running a console command.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; requires non-blank {@code command}. Only {@code CONSOLE} source is supported.
     * @return
     *     Success or validation error response with command execution result.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleExecuteCommand(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String source = arguments.getOrDefault("source", "CONSOLE");
        final String rawCommand = arguments.getOrDefault("command", "");
        if (rawCommand.isBlank())
        {
            return AgentResponses.errorResponse(
                requestId,
                AgentErrorCode.INVALID_ARGUMENT,
                "Argument 'command' must not be blank."
            );
        }

        if (!source.equalsIgnoreCase("CONSOLE"))
        {
            return AgentResponses.errorResponse(
                requestId,
                AgentErrorCode.UNSUPPORTED_SOURCE,
                "Only CONSOLE command source is supported in v1."
            );
        }

        final String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean success = mainThreadExecutor.callOnMainThread(() ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        );

        return AgentResponses.successResponse(requestId, Map.of("success", success.toString()));
    }

    /**
     * Handles {@code BLOCK_TYPE} by returning the material at target coordinates.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; expects {@code worldName}, {@code x}, {@code y}, and {@code z}.
     * @return
     *     Success response containing the resolved block material.
     * @throws Exception
     *     Propagates parsing and main-thread execution failures.
     */
    AgentResponse handleBlockType(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String worldName = arguments.getOrDefault("worldName", "");
        final int x = AgentRequestParsers.parseInt(arguments.getOrDefault("x", "0"));
        final int y = AgentRequestParsers.parseInt(arguments.getOrDefault("y", "0"));
        final int z = AgentRequestParsers.parseInt(arguments.getOrDefault("z", "0"));

        final String materialName = mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            return world.getBlockAt(x, y, z).getType().name();
        });

        return AgentResponses.successResponse(requestId, Map.of("material", materialName));
    }

    /**
     * Handles {@code SET_BLOCK} by setting a block to the requested material.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; expects world coordinates and non-blank {@code material}.
     * @return
     *     Success response containing resulting material name, or validation error response.
     * @throws Exception
     *     Propagates parsing, validation, and main-thread execution failures.
     */
    AgentResponse handleSetBlock(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String worldName = arguments.getOrDefault("worldName", "");
        final int x = AgentRequestParsers.parseInt(arguments.getOrDefault("x", "0"));
        final int y = AgentRequestParsers.parseInt(arguments.getOrDefault("y", "0"));
        final int z = AgentRequestParsers.parseInt(arguments.getOrDefault("z", "0"));
        final String materialName = arguments.getOrDefault("material", "");

        if (materialName.isBlank())
        {
            return AgentResponses.errorResponse(
                requestId,
                AgentErrorCode.INVALID_ARGUMENT,
                "Argument 'material' must not be blank."
            );
        }

        final Material material = AgentRequestParsers.parseMaterial(materialName);
        if (material == null)
        {
            return AgentResponses.errorResponse(
                requestId,
                AgentErrorCode.INVALID_ARGUMENT,
                "Unknown material '%s'.".formatted(materialName)
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

        return AgentResponses.successResponse(requestId, Map.of("material", setMaterial));
    }

    /**
     * Handles {@code WAIT_TICKS} by polling until the tick counter reaches the target value.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; expects non-negative {@code ticks}.
     * @return
     *     Success response with start/end tick values, or timeout/interruption/validation error response.
     */
    AgentResponse handleWaitTicks(String requestId, Map<String, String> arguments)
    {
        final int ticks = AgentRequestParsers.parseInt(arguments.getOrDefault("ticks", "0"));
        if (ticks < 0)
        {
            return AgentResponses.errorResponse(
                requestId,
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
                    requestId,
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
                    requestId,
                    AgentErrorCode.INTERRUPTED,
                    "Interrupted while waiting for ticks."
                );
            }
        }

        return AgentResponses.successResponse(requestId, Map.of(
            "startTick", Long.toString(startTick),
            "endTick", Long.toString(tickCounter.get())
        ));
    }

    /**
     * Handles {@code GET_SERVER_TICK}.
     *
     * @param requestId
     *     Runtime request identifier.
     * @return
     *     Success response with current tick counter value.
     */
    AgentResponse handleGetServerTick(String requestId)
    {
        return AgentResponses.successResponse(requestId, Map.of("tick", Long.toString(tickCounter.get())));
    }
}
