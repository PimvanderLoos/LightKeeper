package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry for synthetic players and related per-player state.
 *
 * <p>All per-player state (player instance, permission attachment, message history, chat component
 * history) is colocated in a single {@link SyntheticPlayerState} entry so lifecycle operations always
 * touch a consistent, atomic view. There is no risk of one of several parallel maps being updated
 * while another is not.
 */
final class AgentSyntheticPlayerStore
{
    /**
     * All per-player state keyed by protocol UUID.
     */
    private final ConcurrentHashMap<UUID, SyntheticPlayerState> players = new ConcurrentHashMap<>();

    /**
     * Resolves a registered synthetic player.
     *
     * @param uuid
     *     Synthetic player UUID.
     * @return
     *     Registered player instance.
     * @throws AgentProtocolException
     *     When the UUID is unknown.
     */
    Player getRequiredPlayer(UUID uuid)
    {
        final SyntheticPlayerState state = players.get(uuid);
        if (state == null)
            throw new AgentProtocolException(
                AgentErrorCode.PLAYER_NOT_REGISTERED,
                "Synthetic player '%s' is not registered.".formatted(uuid));
        return state.player;
    }

    /**
     * Resolves a registered synthetic player that is currently able to perform an in-game action.
     *
     * @param uuid
     *     Synthetic player UUID.
     * @param action
     *     Protocol action being attempted, for failure diagnostics.
     * @return
     *     Active Bukkit player instance.
     * @throws AgentProtocolException
     *     When the UUID is unknown or the player is dead or disconnected.
     */
    Player getRequiredActivePlayer(UUID uuid, String action)
    {
        final SyntheticPlayerState state = players.get(uuid);
        if (state == null)
            throw new AgentProtocolException(
                AgentErrorCode.PLAYER_NOT_REGISTERED,
                "Synthetic player '%s' is not registered.".formatted(uuid));

        final PlayerUnavailability recordedUnavailability = state.unavailability;
        if (recordedUnavailability != null)
            throw unavailableException(uuid, action, recordedUnavailability);

        final Player player = state.player;
        if (player.isDead())
            throw new AgentProtocolException(
                AgentErrorCode.PLAYER_DEAD,
                "Synthetic player '%s' cannot perform %s because the player is dead."
                    .formatted(uuid, action));
        if (!player.isOnline())
            throw new AgentProtocolException(
                AgentErrorCode.PLAYER_DISCONNECTED,
                "Synthetic player '%s' cannot perform %s because the player is disconnected."
                    .formatted(uuid, action));
        return player;
    }

    /**
     * Records a synthetic player death so later action failures retain the event's diagnostic context.
     *
     * @param uuid
     *     Synthetic player UUID.
     * @param details
     *     Human-readable death details captured from the Bukkit event.
     */
    void markPlayerDead(UUID uuid, String details)
    {
        final SyntheticPlayerState state = players.get(uuid);
        if (state != null)
            state.unavailability = new PlayerUnavailability(AgentErrorCode.PLAYER_DEAD, details);
    }

    /**
     * Clears a recorded death after Bukkit has respawned the synthetic player.
     *
     * @param uuid
     *     Synthetic player UUID.
     */
    void markPlayerRespawned(UUID uuid)
    {
        final SyntheticPlayerState state = players.get(uuid);
        if (state != null && state.unavailability != null
            && state.unavailability.errorCode() == AgentErrorCode.PLAYER_DEAD)
            state.unavailability = null;
    }

    /**
     * Records that a synthetic player left the server.
     *
     * @param uuid
     *     Synthetic player UUID.
     * @param details
     *     Human-readable quit details captured from the Bukkit event.
     */
    void markPlayerDisconnected(UUID uuid, String details)
    {
        final SyntheticPlayerState state = players.get(uuid);
        if (state != null)
            state.unavailability = new PlayerUnavailability(AgentErrorCode.PLAYER_DISCONNECTED, details);
    }

    /**
     * Returns whether the UUID belongs to a currently registered synthetic player.
     *
     * @param uuid
     *     Player UUID to inspect.
     * @return {@code true} when the player is managed by this store.
     */
    boolean isSyntheticPlayer(UUID uuid)
    {
        return players.containsKey(uuid);
    }

    private static AgentProtocolException unavailableException(
        UUID uuid, String action, PlayerUnavailability unavailability)
    {
        return new AgentProtocolException(
            unavailability.errorCode(),
            "Synthetic player '%s' cannot perform %s: %s"
                .formatted(uuid, action, unavailability.details()));
    }

    /**
     * Adds a synthetic player to the registry and initializes message tracking.
     *
     * @param uuid
     *     Synthetic player UUID.
     * @param player
     *     Live Bukkit player instance.
     */
    void registerSyntheticPlayer(UUID uuid, Player player)
    {
        players.put(uuid, new SyntheticPlayerState(player));
    }

    /**
     * Applies a comma-separated list of granted permissions to a synthetic player.
     *
     * @param plugin
     *     Plugin context for attachment ownership.
     * @param uuid
     *     Synthetic player UUID.
     * @param player
     *     Target player instance.
     * @param permissionsCsv
     *     Comma-separated permission names.
     */
    void setPermissions(JavaPlugin plugin, UUID uuid, Player player, String permissionsCsv)
    {
        final PermissionAttachment attachment = player.addAttachment(plugin);
        Arrays.stream(permissionsCsv.split(","))
            .map(String::trim)
            .filter(permission -> !permission.isEmpty())
            .forEach(permission -> attachment.setPermission(permission, true));

        final SyntheticPlayerState state = players.get(uuid);
        if (state != null)
            state.permissionAttachment = attachment;
    }

    /**
     * Sets a single permission node on a synthetic player's attachment, creating and storing the attachment
     * first when the player does not have one yet.
     *
     * <p>Unlike {@link #setPermissions}, this reuses the stored attachment so repeated mutations never leak
     * additional attachments on the player.
     *
     * @param plugin
     *     Plugin context for attachment ownership when a new attachment is needed.
     * @param uuid
     *     Synthetic player UUID.
     * @param player
     *     Target player instance.
     * @param permission
     *     The permission node to set.
     * @param value
     *     The value to set the node to: {@code true} grants, {@code false} revokes.
     * @throws IllegalArgumentException
     *     When the UUID is unknown.
     */
    void setPermission(JavaPlugin plugin, UUID uuid, Player player, String permission, boolean value)
    {
        final SyntheticPlayerState state = players.get(uuid);
        if (state == null)
            throw new IllegalArgumentException("Synthetic player '%s' is not registered.".formatted(uuid));

        PermissionAttachment attachment = state.permissionAttachment;
        if (attachment == null)
        {
            attachment = player.addAttachment(plugin);
            state.permissionAttachment = attachment;
        }
        attachment.setPermission(permission, value);
    }

    /**
     * Removes a single permission node from a synthetic player's attachment, restoring the player's default
     * for that node. A no-op when the player has no attachment or the node is not on it.
     *
     * @param uuid
     *     Synthetic player UUID.
     * @param permission
     *     The permission node to remove from the attachment.
     * @throws IllegalArgumentException
     *     When the UUID is unknown.
     */
    void unsetPermission(UUID uuid, String permission)
    {
        final SyntheticPlayerState state = players.get(uuid);
        if (state == null)
            throw new IllegalArgumentException("Synthetic player '%s' is not registered.".formatted(uuid));

        final PermissionAttachment attachment = state.permissionAttachment;
        if (attachment != null)
            attachment.unsetPermission(permission);
    }

    /**
     * Removes and detaches permission attachment state for a synthetic player.
     *
     * @param uuid
     *     Synthetic player UUID.
     * @param player
     *     Target player instance.
     */
    void removePermissionAttachment(UUID uuid, Player player)
    {
        final SyntheticPlayerState state = players.get(uuid);
        if (state == null)
            return;
        final PermissionAttachment attachment = state.permissionAttachment;
        if (attachment != null)
        {
            player.removeAttachment(attachment);
            state.permissionAttachment = null;
        }
    }

    /**
     * Removes a synthetic player and all associated state.
     *
     * @param uuid
     *     Synthetic player UUID.
     */
    void removeSyntheticPlayer(UUID uuid)
    {
        players.remove(uuid);
    }

    /**
     * Returns a stable snapshot of currently registered synthetic player identifiers.
     *
     * @return
     *     Set of registered synthetic player UUIDs.
     */
    Set<UUID> syntheticPlayerIds()
    {
        return Set.copyOf(players.keySet());
    }

    /**
     * Sends a message to a player and records it in tracked history.
     *
     * @param player
     *     Recipient player.
     * @param message
     *     Message text.
     */
    void sendTrackedMessage(Player player, String message)
    {
        player.sendMessage(message);
        final SyntheticPlayerState state = players.get(player.getUniqueId());
        if (state != null)
            state.messageHistory.add(message);
    }

    /**
     * Drains newly received adapter messages and appends them to tracked history.
     *
     * @param nmsAdapter
     *     Adapter used to drain NMS-level captured messages.
     * @param uuid
     *     Synthetic player UUID.
     */
    void capturePlayerMessages(IBotPlayerNmsAdapter nmsAdapter, UUID uuid)
    {
        final List<String> drainedMessages = nmsAdapter.drainReceivedMessages(uuid);
        if (drainedMessages.isEmpty())
            return;

        final SyntheticPlayerState state = players.get(uuid);
        if (state != null)
            state.messageHistory.addAll(drainedMessages);
    }

    /**
     * Drains newly received adapter chat components and appends them to tracked history.
     *
     * @param nmsAdapter
     *     Adapter used to drain NMS-level captured messages.
     * @param uuid
     *     Synthetic player UUID.
     */
    void capturePlayerChatComponents(IBotPlayerNmsAdapter nmsAdapter, UUID uuid)
    {
        final List<String> drainedComponents = nmsAdapter.drainChatComponents(uuid);
        if (drainedComponents.isEmpty())
            return;

        final SyntheticPlayerState state = players.get(uuid);
        if (state != null)
            state.componentHistory.addAll(drainedComponents);
    }

    /**
     * Returns tracked message history for the given synthetic player.
     *
     * @param uuid
     *     Synthetic player UUID.
     * @return
     *     Immutable empty list when unknown; otherwise the tracked history list.
     */
    List<String> getPlayerMessages(UUID uuid)
    {
        final SyntheticPlayerState state = players.get(uuid);
        return state != null ? state.messageHistory : List.of();
    }

    /**
     * Returns tracked chat component history for the given synthetic player.
     *
     * @param uuid
     *     Synthetic player UUID.
     * @return
     *     Immutable empty list when unknown; otherwise the tracked history list.
     */
    List<String> getPlayerChatComponents(UUID uuid)
    {
        final SyntheticPlayerState state = players.get(uuid);
        return state != null ? state.componentHistory : List.of();
    }

    /**
     * Per-player state colocating all mutable fields so lifecycle operations always touch a consistent
     * view.
     */
    private static final class SyntheticPlayerState
    {
        /**
         * Live Bukkit player instance.
         */
        private final Player player;
        /**
         * Permission attachment, or {@code null} when no permissions have been assigned.
         */
        @Nullable private PermissionAttachment permissionAttachment;
        /**
         * Accumulated plain-text message history (direct sends + NMS adapter drains).
         */
        private final List<String> messageHistory = new CopyOnWriteArrayList<>();
        /**
         * Accumulated chat-component JSON history.
         */
        private final List<String> componentHistory = new CopyOnWriteArrayList<>();
        /**
         * Recorded lifecycle reason that currently prevents player actions, or {@code null} while active.
         */
        private volatile @Nullable PlayerUnavailability unavailability;

        private SyntheticPlayerState(Player player)
        {
            this.player = player;
        }
    }

    /**
     * Immutable lifecycle failure retained between its Bukkit event and a later framework action.
     *
     * @param errorCode
     *     Stable protocol error code for the unavailable state.
     * @param details
     *     Human-readable event details.
     */
    private record PlayerUnavailability(AgentErrorCode errorCode, String details)
    {
    }
}
