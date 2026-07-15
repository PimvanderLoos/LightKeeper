package nl.pim16aap2.lightkeeper.nms.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Version-specific NMS bridge for synthetic player lifecycle.
 */
public interface IBotPlayerNmsAdapter
{
    /**
     * Spawns and registers a synthetic player through the internal legacy spawn path.
     *
     * @param uuid
     *     The player UUID.
     * @param name
     *     The player name.
     * @param world
     *     The target world.
     * @param spawnLocation
     *     Spawn location.
     * @return Registered Bukkit player handle.
     */
    Player spawnPlayer(UUID uuid, String name, World world, Location spawnLocation);

    /**
     * Returns the full-login driver for this adapter.
     *
     * <p>Used for {@code FULL_LOGIN} joins; the legacy spawn path above is used for {@code LEGACY_SPAWN}.
     * Implementations may resolve the (larger) login reflection surface lazily, so callers that only ever use
     * the legacy path never pay for it.
     *
     * @return The login driver.
     */
    IBotLoginDriver loginDriver();

    /**
     * Removes a synthetic player from the server.
     *
     * @param player
     *     The player to remove.
     */
    void removePlayer(Player player);

    /**
     * Drains newly received plain-text messages for a synthetic player.
     *
     * @param playerId
     *     Synthetic player UUID.
     * @return Newly captured messages since the previous drain.
     */
    List<String> drainReceivedMessages(UUID playerId);

    /**
     * Drains newly received chat components (JSON format) for a synthetic player.
     *
     * @param playerId
     *     Synthetic player UUID.
     * @return Newly captured chat components since the previous drain.
     */
    List<String> drainChatComponents(UUID playerId);
}
