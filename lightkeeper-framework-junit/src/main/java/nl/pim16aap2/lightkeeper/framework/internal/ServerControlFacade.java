package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IServerControl;
import nl.pim16aap2.lightkeeper.framework.Platform;
import nl.pim16aap2.lightkeeper.framework.ServerErrorsHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Default {@link IServerControl} implementation.
 *
 * <p>Wraps the shared framework internals handed to it by {@link DefaultLightkeeperFramework}: it drives the
 * server process, the agent client, and the player registry, and defers the shared open-state gate,
 * server-down flag, and world preload to the owning framework.
 */
final class ServerControlFacade implements IServerControl
{
    private static final System.Logger LOG = System.getLogger(ServerControlFacade.class.getName());

    private final DefaultLightkeeperFramework framework;
    private final RuntimeManifest runtimeManifest;
    private final MinecraftServerProcess minecraftServerProcess;
    private final UdsAgentClient agentClient;
    private final PlayerScopeRegistry playerScopeRegistry;

    ServerControlFacade(
        DefaultLightkeeperFramework framework,
        RuntimeManifest runtimeManifest,
        MinecraftServerProcess minecraftServerProcess,
        UdsAgentClient agentClient,
        PlayerScopeRegistry playerScopeRegistry)
    {
        this.framework = Objects.requireNonNull(framework, "framework may not be null.");
        this.runtimeManifest = Objects.requireNonNull(runtimeManifest, "runtimeManifest may not be null.");
        this.minecraftServerProcess =
            Objects.requireNonNull(minecraftServerProcess, "minecraftServerProcess may not be null.");
        this.agentClient = Objects.requireNonNull(agentClient, "agentClient may not be null.");
        this.playerScopeRegistry = Objects.requireNonNull(playerScopeRegistry, "playerScopeRegistry may not be null.");
    }

    @Override
    public CommandResult executeCommand(CommandSource source, String command)
    {
        framework.ensureOpen();
        final boolean success = agentClient.executeCommand(source, command);
        return new CommandResult(success, success ? "Command succeeded." : "Command failed.");
    }

    @Override
    public List<String> output()
    {
        framework.ensureOpen();
        return minecraftServerProcess.snapshotOutputLines();
    }

    @Override
    public Platform platform()
    {
        framework.ensureOpen();
        return agentClient.serverPlatform();
    }

    @Override
    public Path directory()
    {
        framework.ensureOpen();
        return Path.of(runtimeManifest.serverDirectory());
    }

    @Override
    public Path pluginDataDirectory(String pluginName)
    {
        framework.ensureOpen();
        final String trimmedPluginName =
            Objects.requireNonNull(pluginName, "pluginName may not be null.").trim();
        if (trimmedPluginName.isEmpty())
            throw new IllegalArgumentException("pluginName may not be blank.");
        // The plugin name is authored by the test writer (trusted input); this check catches accidental path
        // fragments early rather than acting as a security boundary.
        if (trimmedPluginName.contains("/") || trimmedPluginName.contains("\\") || trimmedPluginName.contains(".."))
            throw new IllegalArgumentException(
                "pluginName must be a plain directory name, got '%s'.".formatted(trimmedPluginName));
        return directory().resolve("plugins").resolve(trimmedPluginName);
    }

    @Override
    public void crash()
    {
        framework.ensureOpen();
        LOG.log(System.Logger.Level.INFO, "LK_FRAMEWORK: Crashing Minecraft server.");
        playerScopeRegistry.invalidateAll();
        agentClient.close();
        minecraftServerProcess.kill();
        framework.markServerDown();
    }

    @Override
    public void stop()
    {
        framework.ensureOpen();
        if (!minecraftServerProcess.isRunning())
            throw new IllegalStateException("Cannot stop the server because it is not running.");
        doStop();
    }

    /**
     * Graceful-stop implementation without the running-state precondition.
     *
     * <p>Tolerates a server that died on its own after the caller's running check: player cleanup swallows
     * per-player failures, the client close is idempotent, and the process stop handles dead processes. This is
     * what lets {@link #restart()} avoid a check-then-act race on the running state.
     */
    private void doStop()
    {
        LOG.log(System.Logger.Level.INFO, "LK_FRAMEWORK: Stopping Minecraft server gracefully.");
        try
        {
            // Remove synthetic players through the live agent first so they quit cleanly instead of being
            // persisted as offline players in the world's playerdata by the server's shutdown save.
            playerScopeRegistry.cleanupAll(agentClient::removePlayer);
            agentClient.close();
        }
        finally
        {
            framework.markServerDown();
            minecraftServerProcess.stop(DefaultLightkeeperFramework.SHUTDOWN_TIMEOUT);
        }
    }

    @Override
    public void start()
    {
        framework.ensureOpen();
        if (minecraftServerProcess.isRunning())
            throw new IllegalStateException("Cannot start the server because it is already running.");

        LOG.log(System.Logger.Level.INFO, "LK_FRAMEWORK: Starting Minecraft server.");
        minecraftServerProcess.start(DefaultLightkeeperFramework.STARTUP_TIMEOUT);
        try
        {
            agentClient.rehandshake(
                DefaultLightkeeperFramework.AGENT_CONNECT_TIMEOUT,
                runtimeManifest.agentAuthToken(),
                runtimeManifest.runtimeProtocolVersion(),
                Objects.requireNonNullElse(runtimeManifest.agentJarSha256(), "")
            );
            framework.preloadConfiguredWorlds();
        }
        catch (Exception exception)
        {
            // Return to a clean 'down' state so a retry of start() remains possible; leaving the process running
            // here would wedge the start path (running + down means only restart() could recover).
            try
            {
                minecraftServerProcess.stop(DefaultLightkeeperFramework.SHUTDOWN_TIMEOUT);
            }
            catch (Exception stopException)
            {
                exception.addSuppressed(stopException);
            }
            throw exception;
        }
        framework.markServerUp();
    }

    @Override
    public void restart()
    {
        framework.ensureOpen();
        LOG.log(System.Logger.Level.INFO, "LK_FRAMEWORK: Restarting Minecraft server.");
        if (minecraftServerProcess.isRunning())
            doStop();
        start();
    }

    @Override
    public long currentTick()
    {
        framework.ensureOpen();
        return agentClient.getServerTick();
    }

    @Override
    public ServerErrorsHandle errors()
    {
        framework.ensureOpen();
        return FrameworkHandleFactory.serverErrorsHandle(framework);
    }
}
