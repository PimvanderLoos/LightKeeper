package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
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
     * @throws IllegalArgumentException
     *     When the UUID is unknown.
     */
    Player getRequiredPlayer(UUID uuid)
    {
        final SyntheticPlayerState state = players.get(uuid);
        if (state == null)
            throw new IllegalArgumentException("Synthetic player '%s' is not registered.".formatted(uuid));
        return state.player;
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
        final Player player;
        /**
         * Permission attachment, or {@code null} when no permissions have been assigned.
         */
        @Nullable PermissionAttachment permissionAttachment;
        /**
         * Accumulated plain-text message history (direct sends + NMS adapter drains).
         */
        final List<String> messageHistory = new CopyOnWriteArrayList<>();
        /**
         * Accumulated chat-component JSON history.
         */
        final List<String> componentHistory = new CopyOnWriteArrayList<>();

        private SyntheticPlayerState(Player player)
        {
            this.player = player;
        }
    }
}
