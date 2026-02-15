package nl.pim16aap2.lightkeeper.agent.spigot;

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

final class AgentWorldActions
{
    private static final long WAIT_TICKS_TIMEOUT_MILLIS = 60_000L;

    private final JavaPlugin plugin;
    private final AgentMainThreadExecutor mainThreadExecutor;
    private final AtomicLong tickCounter;

    AgentWorldActions(JavaPlugin plugin, AgentMainThreadExecutor mainThreadExecutor, AtomicLong tickCounter)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.tickCounter = Objects.requireNonNull(tickCounter, "tickCounter");
    }

    void incrementTick()
    {
        tickCounter.incrementAndGet();
    }

    AgentResponse handleMainWorld(String requestId)
        throws Exception
    {
        final World mainWorld = mainThreadExecutor.callOnMainThread(() -> Bukkit.getWorlds().getFirst());
        return AgentResponses.successResponse(requestId, Map.of("worldName", mainWorld.getName()));
    }

    AgentResponse handleNewWorld(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String worldName = arguments.getOrDefault("worldName", "").trim();
        if (worldName.isBlank())
        {
            return AgentResponses.errorResponse(
                requestId,
                "INVALID_ARGUMENT",
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

    AgentResponse handleExecuteCommand(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String source = arguments.getOrDefault("source", "CONSOLE");
        final String rawCommand = arguments.getOrDefault("command", "");
        if (rawCommand.isBlank())
        {
            return AgentResponses.errorResponse(
                requestId,
                "INVALID_ARGUMENT",
                "Argument 'command' must not be blank."
            );
        }

        if (!source.equalsIgnoreCase("CONSOLE"))
        {
            return AgentResponses.errorResponse(
                requestId,
                "UNSUPPORTED_SOURCE",
                "Only CONSOLE command source is supported in v1."
            );
        }

        final String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean success = mainThreadExecutor.callOnMainThread(() ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        );

        return AgentResponses.successResponse(requestId, Map.of("success", success.toString()));
    }

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
                "INVALID_ARGUMENT",
                "Argument 'material' must not be blank."
            );
        }

        final Material material = AgentRequestParsers.parseMaterial(materialName);
        if (material == null)
        {
            return AgentResponses.errorResponse(
                requestId,
                "INVALID_ARGUMENT",
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

    AgentResponse handleWaitTicks(String requestId, Map<String, String> arguments)
    {
        final int ticks = AgentRequestParsers.parseInt(arguments.getOrDefault("ticks", "0"));
        if (ticks < 0)
        {
            return AgentResponses.errorResponse(
                requestId,
                "INVALID_ARGUMENT",
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
                    "TIMEOUT",
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
                    "INTERRUPTED",
                    "Interrupted while waiting for ticks."
                );
            }
        }

        return AgentResponses.successResponse(requestId, Map.of(
            "startTick", Long.toString(startTick),
            "endTick", Long.toString(tickCounter.get())
        ));
    }

    AgentResponse handleGetServerTick(String requestId)
    {
        return AgentResponses.successResponse(requestId, Map.of("tick", Long.toString(tickCounter.get())));
    }
}
