package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IBots;
import nl.pim16aap2.lightkeeper.framework.IPlayerBuilder;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link IBots} implementation.
 *
 * <p>Wraps the shared framework internals handed to it by {@link DefaultLightkeeperFramework}: it creates
 * synthetic players through the agent client and tracks them in the player registry, deferring the shared
 * open-state gate to the owning framework.
 */
final class BotsFacade implements IBots
{
    private static final System.Logger LOG = System.getLogger(BotsFacade.class.getName());

    private final DefaultLightkeeperFramework framework;
    private final UdsAgentClient agentClient;
    private final PlayerScopeRegistry playerScopeRegistry;

    BotsFacade(
        DefaultLightkeeperFramework framework,
        UdsAgentClient agentClient,
        PlayerScopeRegistry playerScopeRegistry)
    {
        this.framework = Objects.requireNonNull(framework, "framework may not be null.");
        this.agentClient = Objects.requireNonNull(agentClient, "agentClient may not be null.");
        this.playerScopeRegistry = Objects.requireNonNull(playerScopeRegistry, "playerScopeRegistry may not be null.");
    }

    @Override
    public PlayerHandle join(String name, WorldHandle world)
    {
        return join(name, UUID.randomUUID(), world);
    }

    @Override
    public PlayerHandle join(String name, UUID uuid, WorldHandle world)
    {
        framework.ensureOpen();
        final String trimmedName = DefaultLightkeeperFramework.validatePlayerName(name);
        Objects.requireNonNull(uuid, "uuid may not be null.");
        Objects.requireNonNull(world, "world may not be null.");
        final String worldName = Objects.requireNonNull(world.name(), "world.name may not be null.");

        final AgentPlayerData createdPlayer = agentClient.createPlayer(
            trimmedName,
            uuid,
            worldName,
            null,
            null,
            null,
            null,
            null
        );
        final PlayerHandle handle = registerAndWrap(createdPlayer);
        LOG.log(
            System.Logger.Level.INFO,
            () -> "LK_FRAMEWORK: Created player '%s' (%s) in world '%s'."
                .formatted(createdPlayer.name(), createdPlayer.uniqueId(), worldName)
        );
        return handle;
    }

    @Override
    public IPlayerBuilder builder()
    {
        framework.ensureOpen();
        return new DefaultPlayerBuilder(framework);
    }

    /**
     * Creates a synthetic player from fully-resolved builder inputs.
     *
     * <p>Called by {@link DefaultPlayerBuilder} through {@link DefaultLightkeeperFramework}; the open-state gate is
     * enforced by the builder before this runs.
     */
    PlayerHandle createFromBuilder(
        String name,
        UUID uuid,
        WorldHandle worldHandle,
        @Nullable Double x,
        @Nullable Double y,
        @Nullable Double z,
        @Nullable Double health,
        Set<String> permissions)
    {
        final AgentPlayerData createdPlayer = agentClient.createPlayer(
            name,
            uuid,
            worldHandle.name(),
            x,
            y,
            z,
            health,
            permissions
        );
        return registerAndWrap(createdPlayer);
    }

    private PlayerHandle registerAndWrap(AgentPlayerData createdPlayer)
    {
        playerScopeRegistry.register(createdPlayer.uniqueId());
        return FrameworkHandleFactory.playerHandle(framework, createdPlayer.uniqueId(), createdPlayer.name());
    }
}
