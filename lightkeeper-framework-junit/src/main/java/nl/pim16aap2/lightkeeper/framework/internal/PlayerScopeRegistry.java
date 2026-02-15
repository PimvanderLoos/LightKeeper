package nl.pim16aap2.lightkeeper.framework.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Tracks synthetic players and their resource scope for test lifecycle cleanup.
 */
@Singleton
final class PlayerScopeRegistry
{
    private static final System.Logger LOGGER = System.getLogger(PlayerScopeRegistry.class.getName());

    private final Map<UUID, ScopeBinding> createdPlayers = new ConcurrentHashMap<>();
    private final ThreadLocal<ScopeBinding> activeResourceScope = ThreadLocal.withInitial(ScopeBinding::classScope);

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

    void beginMethodScope(String methodExecutionId)
    {
        activeResourceScope.set(ScopeBinding.methodScope(validateMethodExecutionId(methodExecutionId)));
    }

    void endMethodScope(String methodExecutionId, Consumer<UUID> playerRemover)
    {
        final String resolvedMethodExecutionId = validateMethodExecutionId(methodExecutionId);
        cleanupPlayersByScope(
            scopeBinding ->
                scopeBinding.scope() == ResourceScope.METHOD &&
                    resolvedMethodExecutionId.equals(scopeBinding.methodExecutionId()),
            playerRemover
        );
        activeResourceScope.set(ScopeBinding.classScope());
    }

    void cleanupAll(Consumer<UUID> playerRemover)
    {
        cleanupPlayersByScope(scopeBinding -> true, playerRemover);
    }

    private void cleanupPlayersByScope(Predicate<ScopeBinding> scopeSelector, Consumer<UUID> playerRemover)
    {
        final Map<UUID, ScopeBinding> playerSnapshot = Map.copyOf(createdPlayers);
        for (final Map.Entry<UUID, ScopeBinding> entry : playerSnapshot.entrySet())
        {
            final UUID playerId = entry.getKey();
            final ScopeBinding scopeBinding = entry.getValue();
            if (!scopeSelector.test(scopeBinding))
                continue;
            try
            {
                playerRemover.accept(playerId);
            }
            catch (Exception exception)
            {
                LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Failed to remove synthetic player from scope cleanup: " + playerId,
                    exception
                );
            }
            finally
            {
                createdPlayers.remove(playerId, scopeBinding);
            }
        }
    }

    private static String validateMethodExecutionId(String methodExecutionId)
    {
        final String trimmedMethodExecutionId =
            Objects.requireNonNull(methodExecutionId, "methodExecutionId may not be null.").trim();
        if (trimmedMethodExecutionId.isEmpty())
            throw new IllegalArgumentException("methodExecutionId may not be blank.");
        return trimmedMethodExecutionId;
    }

    private record ScopeBinding(ResourceScope scope, @Nullable String methodExecutionId)
    {
        private ScopeBinding
        {
            Objects.requireNonNull(scope, "scope may not be null.");
            if (scope == ResourceScope.CLASS && methodExecutionId != null)
                throw new IllegalArgumentException("methodExecutionId must be null for CLASS scope.");
            if (scope == ResourceScope.METHOD && methodExecutionId == null)
                throw new IllegalArgumentException("methodExecutionId may not be null for METHOD scope.");
        }

        private static ScopeBinding classScope()
        {
            return new ScopeBinding(ResourceScope.CLASS, null);
        }

        private static ScopeBinding methodScope(String methodExecutionId)
        {
            return new ScopeBinding(ResourceScope.METHOD, methodExecutionId);
        }
    }

    private enum ResourceScope
    {
        CLASS,
        METHOD
    }
}
