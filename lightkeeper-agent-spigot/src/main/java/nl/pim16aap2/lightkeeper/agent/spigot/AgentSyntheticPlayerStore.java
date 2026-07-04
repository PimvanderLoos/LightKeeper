package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry for synthetic players and related per-player state.
 *
 * <p>The store tracks:
 * <ul>
 *   <li>Registered synthetic player instances by UUID.</li>
 *   <li>Permission attachments created for those players.</li>
 *   <li>Message history merged from direct sends and NMS adapter drains.</li>
 * </ul>
 */
final class AgentSyntheticPlayerStore
{
    /**
     * Synthetic players registered by protocol UUID.
     */
    private final ConcurrentHashMap<UUID, Player> syntheticPlayers = new ConcurrentHashMap<>();
    /**
     * Permission attachments associated with synthetic players.
     */
    private final ConcurrentHashMap<UUID, PermissionAttachment> permissionAttachments = new ConcurrentHashMap<>();
    /**
     * Aggregated player message history by synthetic player UUID.
     */
    private final ConcurrentHashMap<UUID, List<String>> playerMessageHistory = new ConcurrentHashMap<>();

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
        final Player player = syntheticPlayers.get(uuid);
        if (player == null)
            throw new IllegalArgumentException("Synthetic player '%s' is not registered.".formatted(uuid));
        return player;
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
        syntheticPlayers.put(uuid, player);
        playerMessageHistory.put(uuid, new CopyOnWriteArrayList<>());
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
        permissionAttachments.put(uuid, attachment);
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
        final PermissionAttachment attachment = permissionAttachments.remove(uuid);
        if (attachment != null)
            player.removeAttachment(attachment);
    }

    /**
     * Removes a synthetic player and all associated message tracking state.
     *
     * @param uuid
     *     Synthetic player UUID.
     */
    void removeSyntheticPlayer(UUID uuid)
    {
        playerMessageHistory.remove(uuid);
        syntheticPlayers.remove(uuid);
    }

    /**
     * Returns a stable snapshot of currently registered synthetic player identifiers.
     *
     * @return
     *     Set of registered synthetic player UUIDs.
     */
    Set<UUID> syntheticPlayerIds()
    {
        return Set.copyOf(syntheticPlayers.keySet());
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
        playerMessageHistory
            .computeIfAbsent(player.getUniqueId(), ignored -> new CopyOnWriteArrayList<>())
            .add(message);
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

        playerMessageHistory
            .computeIfAbsent(uuid, ignored -> new CopyOnWriteArrayList<>())
            .addAll(drainedMessages);
    }

    /**
     * Returns tracked message history for the given synthetic player.
     *
     * @param uuid
     *     Synthetic player UUID.
     * @return
     *     Immutable empty list when unknown; otherwise tracked history list.
     */
    List<String> getPlayerMessages(UUID uuid)
    {
        return playerMessageHistory.getOrDefault(uuid, List.of());
    }
}
