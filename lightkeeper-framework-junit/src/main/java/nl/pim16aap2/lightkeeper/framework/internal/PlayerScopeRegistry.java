package nl.pim16aap2.lightkeeper.framework.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks synthetic players and their resource scope for test lifecycle cleanup.
 */
@Singleton
final class PlayerScopeRegistry
{
    private final Map<UUID, ResourceScope> createdPlayers = new ConcurrentHashMap<>();
    private final ThreadLocal<ResourceScope> activeResourceScope = ThreadLocal.withInitial(() -> ResourceScope.CLASS);

    @Inject
    PlayerScopeRegistry()
    {
    }

    void register(UUID playerId)
    {
        createdPlayers.put(playerId, activeResourceScope.get());
    }

    void unregister(UUID playerId)
    {
        createdPlayers.remove(playerId);
    }

    void beginMethodScope()
    {
        activeResourceScope.set(ResourceScope.METHOD);
    }

    void endMethodScope(Consumer<UUID> playerRemover)
    {
        cleanupPlayersByScope(ResourceScope.METHOD, playerRemover);
        activeResourceScope.set(ResourceScope.CLASS);
    }

    void cleanupAll(Consumer<UUID> playerRemover)
    {
        cleanupPlayersByScope(null, playerRemover);
    }

    private void cleanupPlayersByScope(ResourceScope scope, Consumer<UUID> playerRemover)
    {
        final Set<UUID> playerIds = new HashSet<>(createdPlayers.keySet());
        for (UUID playerId : playerIds)
        {
            final ResourceScope playerScope = createdPlayers.get(playerId);
            if (playerScope == null)
                continue;
            if (scope != null && playerScope != scope)
                continue;
            try
            {
                playerRemover.accept(playerId);
            }
            catch (Exception ignored)
            {
            }
            finally
            {
                createdPlayers.remove(playerId);
            }
        }
    }

    private enum ResourceScope
    {
        CLASS,
        METHOD
    }
}
