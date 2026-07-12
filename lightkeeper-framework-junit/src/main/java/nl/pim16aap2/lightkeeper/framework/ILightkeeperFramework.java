package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.CommandSource;

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
     * Crashes the Minecraft server immediately by force-killing the process.
     *
     * <p>All fixtures created before the crash are invalidated: player and world handles obtained earlier no
     * longer refer to live server state. In shared-server mode the server must be restarted via
     * {@link #restartServer()} before the next test method runs, otherwise that method fails fast; annotate the
     * test with {@code @FreshServer} to receive a new server per method instead.
     */
    void crashServer();

    /**
     * Restarts the Minecraft server after a {@link #crashServer()} call.
     *
     * <p>Only worlds configured in the runtime manifest are preloaded again. Players and worlds created at
     * runtime before the crash are <strong>not</strong> recreated; tests must re-establish their own fixtures
     * after restarting.
     *
     * @throws IllegalStateException
     *     If the server is still running, since a restart is only valid after a crash.
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
