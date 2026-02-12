package nl.pim16aap2.lightkeeper.nms.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Version-specific NMS bridge for synthetic player lifecycle.
 */
public interface BotPlayerNmsAdapter
{
    /**
     * Spawns and registers a synthetic player.
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
     * Removes a synthetic player from the server.
     *
     * @param player
     *     The player to remove.
     */
    void removePlayer(Player player);
}
