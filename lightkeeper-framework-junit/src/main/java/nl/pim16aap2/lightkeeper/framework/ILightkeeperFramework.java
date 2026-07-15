package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.CommandSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * LightKeeper end-to-end test framework entrypoint.
 *
 * <p>The framework surface is organised into facets, each returned by an accessor: {@link #server()},
 * {@link #worlds()}, {@link #bots()}, and {@link #events()}. Prefer these facet accessors: the flat v1 methods
 * on this interface are deprecated for removal and delegate into the facets.
 */
public interface ILightkeeperFramework extends AutoCloseable
{
    /**
     * Gets the server-control facet: command execution, console output, platform, filesystem access, process
     * lifecycle, tick counter, and captured server errors.
     *
     * @return The server-control facet.
     */
    IServerControl server();

    /**
     * Gets the worlds facet: create worlds from defaults, specs, or provisioned templates, or via a builder.
     *
     * @return The worlds facet.
     */
    IWorlds worlds();

    /**
     * Gets the bots facet: join synthetic players into a world, or configure one via a builder.
     *
     * @return The bots facet.
     */
    IBots bots();

    /**
     * Gets the events facet: capture Bukkit events for later inspection.
     *
     * @return The events facet.
     */
    IEvents events();

    /**
     * Gets the main world handle.
     *
     * @return The main world handle.
     * @deprecated Use {@link IWorlds#main()} via {@link #worlds()}.
     */
    @Deprecated(forRemoval = true)
    default WorldHandle mainWorld()
    {
        return worlds().main();
    }

    /**
     * Creates a new world using framework defaults.
     *
     * @return The created world handle.
     * @deprecated Use {@link IWorlds#create()} via {@link #worlds()}.
     */
    @Deprecated(forRemoval = true)
    default WorldHandle newWorld()
    {
        return worlds().create();
    }

    /**
     * Creates a new world from a world spec.
     *
     * @param worldSpec
     *     The world specification.
     * @return The created world handle.
     * @deprecated Use {@link IWorlds#create(WorldSpec)} via {@link #worlds()}.
     */
    @Deprecated(forRemoval = true)
    default WorldHandle newWorld(WorldSpec worldSpec)
    {
        return worlds().create(worldSpec);
    }

    /**
     * Loads a world from a pre-provisioned template folder.
     *
     * @param templateName
     *     The provisioned world folder's name.
     * @return A handle for the loaded world.
     * @deprecated Use {@link IWorlds#fromTemplate(String)} via {@link #worlds()}.
     */
    @Deprecated(forRemoval = true)
    default WorldHandle newWorldFromTemplate(String templateName)
    {
        return worlds().fromTemplate(templateName);
    }

    /**
     * Creates a synthetic player in a world at spawn.
     *
     * @param name
     *     Player name.
     * @param world
     *     Target world.
     * @return Created player handle.
     * @deprecated Use {@link IBots#join(String, WorldHandle)} via {@link #bots()}.
     */
    @Deprecated(forRemoval = true)
    default PlayerHandle createPlayer(String name, WorldHandle world)
    {
        return bots().join(name, world);
    }

    /**
     * Creates a synthetic player in a world at spawn.
     *
     * @param name
     *     Player name.
     * @param uuid
     *     Player UUID.
     * @param world
     *     Target world.
     * @return Created player handle.
     * @deprecated Use {@link IBots#join(String, UUID, WorldHandle)} via {@link #bots()}.
     */
    @Deprecated(forRemoval = true)
    default PlayerHandle createPlayer(String name, UUID uuid, WorldHandle world)
    {
        return bots().join(name, uuid, world);
    }

    /**
     * Creates a player builder.
     *
     * @return A new player builder.
     * @deprecated Use {@link IBots#builder()} via {@link #bots()}.
     */
    @Deprecated(forRemoval = true)
    default IPlayerBuilder buildPlayer()
    {
        return bots().builder();
    }

    /**
     * Creates a world builder.
     *
     * @return A new world builder.
     * @deprecated Use {@link IWorlds#builder()} via {@link #worlds()}.
     */
    @Deprecated(forRemoval = true)
    default IWorldBuilder buildWorld()
    {
        return worlds().builder();
    }

    /**
     * Executes a command from the requested source.
     *
     * @param source
     *     The command source.
     * @param command
     *     The command text.
     * @return Command result.
     * @deprecated Use {@link IServerControl#executeCommand(CommandSource, String)} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default CommandResult executeCommand(CommandSource source, String command)
    {
        return server().executeCommand(source, command);
    }

    /**
     * Waits until a condition is true or timeout expires.
     *
     * @param condition
     *     The condition.
     * @param timeout
     *     Timeout duration.
     */
    void waitUntil(Condition condition, Duration timeout);

    /**
     * Gets a snapshot of captured Minecraft server output lines.
     *
     * @return Captured server output lines ordered oldest-to-newest.
     * @deprecated Use {@link IServerControl#output()} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default List<String> serverOutput()
    {
        return server().output();
    }

    /**
     * Gets the server platform (e.g. PAPER, SPIGOT).
     *
     * @return Server platform.
     * @deprecated Use {@link IServerControl#platform()} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default Platform platform()
    {
        return server().platform();
    }

    /**
     * Gets the Minecraft server's working directory.
     *
     * @return The server directory.
     * @deprecated Use {@link IServerControl#directory()} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default Path serverDirectory()
    {
        return server().directory();
    }

    /**
     * Gets the data directory of a plugin inside the server's {@code plugins} directory.
     *
     * @param pluginName
     *     The plugin's name, as used for its data directory (usually the {@code name} from its
     *     {@code plugin.yml}).
     * @return The plugin data directory.
     * @deprecated Use {@link IServerControl#pluginDataDirectory(String)} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default Path pluginDataDirectory(String pluginName)
    {
        return server().pluginDataDirectory(pluginName);
    }

    /**
     * Crashes the Minecraft server immediately by force-killing the process.
     *
     * @deprecated Use {@link IServerControl#crash()} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default void crashServer()
    {
        server().crash();
    }

    /**
     * Stops the Minecraft server gracefully, force-killing it only when it does not exit within the shutdown
     * timeout.
     *
     * @throws IllegalStateException
     *     If the server is not running.
     * @deprecated Use {@link IServerControl#stop()} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default void stopServer()
    {
        server().stop();
    }

    /**
     * Starts the Minecraft server after a {@link #stopServer()} or {@link #crashServer()} call.
     *
     * @throws IllegalStateException
     *     If the server is already running.
     * @deprecated Use {@link IServerControl#start()} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default void startServer()
    {
        server().start();
    }

    /**
     * Restarts the Minecraft server.
     *
     * @deprecated Use {@link IServerControl#restart()} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default void restartServer()
    {
        server().restart();
    }

    /**
     * Starts capturing Bukkit events of the specified type.
     *
     * @param eventClassName
     *     The full class name of the event to capture (e.g. "org.bukkit.event.player.PlayerMoveEvent").
     * @return A handle to manage the capture session.
     * @deprecated Use {@link IEvents#capture(String)} via {@link #events()}.
     */
    @Deprecated(forRemoval = true)
    default EventCaptureHandle captureEvents(String eventClassName)
    {
        return events().capture(eventClassName);
    }

    /**
     * Gets the agent's monotonic tick counter, for correlating against {@link CapturedEventSnapshot#tick()}
     * stamps.
     *
     * @return The monotonic, session-relative server tick.
     * @deprecated Use {@link IServerControl#currentTick()} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default long currentServerTick()
    {
        return server().currentTick();
    }

    /**
     * Gets a handle over the always-on server-error capture.
     *
     * @return A handle exposing the captured server errors.
     * @deprecated Use {@link IServerControl#errors()} via {@link #server()}.
     */
    @Deprecated(forRemoval = true)
    default ServerErrorsHandle serverErrors()
    {
        return server().errors();
    }

    @Override
    void close();
}
