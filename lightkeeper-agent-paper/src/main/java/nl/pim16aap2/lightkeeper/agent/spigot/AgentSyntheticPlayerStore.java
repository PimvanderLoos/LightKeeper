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

final class AgentSyntheticPlayerStore
{
    private final ConcurrentHashMap<UUID, Player> syntheticPlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PermissionAttachment> permissionAttachments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<String>> playerMessageHistory = new ConcurrentHashMap<>();

    Player getRequiredPlayer(UUID uuid)
    {
        final Player player = syntheticPlayers.get(uuid);
        if (player == null)
            throw new IllegalArgumentException("Synthetic player '%s' is not registered.".formatted(uuid));
        return player;
    }

    void registerSyntheticPlayer(UUID uuid, Player player)
    {
        syntheticPlayers.put(uuid, player);
        playerMessageHistory.put(uuid, new CopyOnWriteArrayList<>());
    }

    void setPermissions(JavaPlugin plugin, UUID uuid, Player player, String permissionsCsv)
    {
        final PermissionAttachment attachment = player.addAttachment(plugin);
        Arrays.stream(permissionsCsv.split(","))
            .map(String::trim)
            .filter(permission -> !permission.isEmpty())
            .forEach(permission -> attachment.setPermission(permission, true));
        permissionAttachments.put(uuid, attachment);
    }

    void removePermissionAttachment(UUID uuid, Player player)
    {
        final PermissionAttachment attachment = permissionAttachments.remove(uuid);
        if (attachment != null)
            player.removeAttachment(attachment);
    }

    void removeSyntheticPlayer(UUID uuid)
    {
        playerMessageHistory.remove(uuid);
        syntheticPlayers.remove(uuid);
    }

    Set<UUID> syntheticPlayerIds()
    {
        return Set.copyOf(syntheticPlayers.keySet());
    }

    void sendTrackedMessage(Player player, String message)
    {
        player.sendMessage(message);
        playerMessageHistory
            .computeIfAbsent(player.getUniqueId(), ignored -> new CopyOnWriteArrayList<>())
            .add(message);
    }

    void capturePlayerMessages(IBotPlayerNmsAdapter nmsAdapter, UUID uuid)
    {
        final List<String> drainedMessages = nmsAdapter.drainReceivedMessages(uuid);
        if (drainedMessages.isEmpty())
            return;

        playerMessageHistory
            .computeIfAbsent(uuid, ignored -> new CopyOnWriteArrayList<>())
            .addAll(drainedMessages);
    }

    List<String> getPlayerMessages(UUID uuid)
    {
        return playerMessageHistory.getOrDefault(uuid, List.of());
    }
}
