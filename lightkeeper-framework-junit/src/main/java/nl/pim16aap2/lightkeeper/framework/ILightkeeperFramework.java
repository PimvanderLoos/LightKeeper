package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.CommandSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * LightKeeper end-to-end test framework entrypoint.
 */
public interface ILightkeeperFramework extends AutoCloseable
{
    /**
     * Gets the main world handle.
     *
     * @return The main world handle.
     */
    WorldHandle mainWorld();

    /**
     * Creates a new world using framework defaults.
     *
     * @return The created world handle.
     */
    WorldHandle newWorld();

    /**
     * Creates a new world from a world spec.
     *
     * @param worldSpec
     *     The world specification.
     * @return The created world handle.
     */
    WorldHandle newWorld(WorldSpec worldSpec);

    /**
     * Creates a synthetic player in a world at spawn.
     *
     * @param name
     *     Player name.
     * @param world
     *     Target world.
     * @return Created player handle.
     */
    PlayerHandle createPlayer(String name, WorldHandle world);

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
     */
    PlayerHandle createPlayer(String name, UUID uuid, WorldHandle world);

    /**
     * Creates a player builder.
     *
     * @return A new player builder.
     */
    IPlayerBuilder buildPlayer();

    /**
     * Creates a world builder.
     *
     * @return A new world builder.
     */
    IWorldBuilder buildWorld();

    /**
     * Executes a command from the requested source.
     *
     * @param source
     *     The command source.
     * @param command
     *     The command text.
     * @return Command result.
     */
    CommandResult executeCommand(CommandSource source, String command);

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
     */
    List<String> serverOutput();

    /**
     * Gets the server platform (e.g. PAPER, SPIGOT).
     *
     * @return Server platform.
     */
    Platform platform();

    /**
     * Gets the Minecraft server's working directory.
     *
     * <p>Filesystem contract: reading is safe at any time; writing (e.g. seeding database files or patching
     * plugin configurations) is only safe while the server is stopped — between {@link #stopServer()} and
     * {@link #startServer()} — because the server reads most files once at boot and may overwrite them on
     * shutdown.
     *
     * @return The server directory.
     */
    Path serverDirectory();

    /**
     * Gets the data directory of a plugin inside the server's {@code plugins} directory.
     *
     * <p>The directory may not exist yet, e.g. before the plugin's first boot. The filesystem contract of
     * {@link #serverDirectory()} applies.
     *
     * @param pluginName
     *     The plugin's name, as used for its data directory (usually the {@code name} from its
     *     {@code plugin.yml}).
     * @return The plugin data directory.
     */
    Path pluginDataDirectory(String pluginName);

    /**
     * Crashes the Minecraft server immediately by force-killing the process.
     *
     * <p>All fixtures created before the crash are invalidated: player and world handles obtained earlier no
     * longer refer to live server state. In shared-server mode the server must be started again via
     * {@link #startServer()} or {@link #restartServer()} before the next test method runs, otherwise that method
     * fails fast; annotate the test with {@code @FreshServer} to receive a new server per method instead.
     */
    void crashServer();

    /**
     * Stops the Minecraft server gracefully via the console {@code stop} command, force-killing it only when it
     * does not exit within the shutdown timeout.
     *
     * <p>Synthetic players are removed before the server shuts down so they quit cleanly. All fixtures created
     * before the stop are invalidated, exactly as documented on {@link #crashServer()}. While the server is
     * stopped, the server directory may be modified freely — see {@link #serverDirectory()}.
     *
     * @throws IllegalStateException
     *     If the server is not running.
     */
    void stopServer();

    /**
     * Starts the Minecraft server after a {@link #stopServer()} or {@link #crashServer()} call.
     *
     * <p>Only worlds configured in the runtime manifest are preloaded again. Players and worlds created at
     * runtime before the server went down are <strong>not</strong> recreated; tests must re-establish their own
     * fixtures after starting.
     *
     * @throws IllegalStateException
     *     If the server is already running.
     */
    void startServer();

    /**
     * Restarts the Minecraft server: a graceful {@link #stopServer()} when it is running, followed by
     * {@link #startServer()}.
     *
     * <p>Also valid when the server is already down (after {@link #stopServer()} or {@link #crashServer()}), in
     * which case it only starts the server. See {@link #startServer()} for what is — and is not — restored.
     */
    void restartServer();

    /**
     * Starts capturing Bukkit events of the specified type.
     *
     * @param eventClassName
     *     The full class name of the event to capture (e.g. "org.bukkit.event.player.PlayerMoveEvent").
     * @return A handle to manage the capture session.
     */
    EventCaptureHandle captureEvents(String eventClassName);

    /**
     * Gets a handle over the always-on server-error capture.
     *
     * <p>The agent captures every WARN-or-worse log event inside the server as a structured snapshot — with the
     * real throwable class, message, and cause chain — from the moment the agent plugin loads (before any
     * plugin's {@code onEnable}). Failures inside the logging system itself (reported via Log4j's status
     * logger) and stack traces written to the server process's raw stderr file descriptor are captured as well.
     *
     * <p>In shared-server mode the capture buffer is cleared automatically at the end of every test method, so
     * each test observes only the errors of its own window; the first test's window also covers server boot.
     * Known gaps: exceptions that are caught and never logged are invisible, as are log events emitted before
     * the agent plugin loads (use {@link #serverOutput()} for pre-boot inspection).
     *
     * @return A handle exposing the captured server errors.
     */
    ServerErrorsHandle serverErrors();

    @Override
    void close();
}
