package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.Condition;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Internal runtime gateway used by handle types.
 */
public interface IFrameworkGateway
{
    /**
     * Gets block material at a position.
     */
    String getBlock(String worldName, Vector3Di position);

    /**
     * Sets block material at a position.
     */
    void setBlock(String worldName, Vector3Di position, String material);

    /**
     * Executes a command as a synthetic player.
     */
    void executePlayerCommand(UUID playerId, String command);

    /**
     * Places a block as a synthetic player.
     */
    void placePlayerBlock(UUID playerId, String material, int x, int y, int z);

    /**
     * Retrieves menu snapshot for a synthetic player.
     */
    MenuSnapshot menuSnapshot(UUID playerId);

    /**
     * Clicks a menu slot for a synthetic player.
     */
    void clickMenuSlot(UUID playerId, int slot);

    /**
     * Performs a menu drag interaction for a synthetic player.
     */
    void dragMenuSlots(UUID playerId, String materialKey, int... slots);

    /**
     * Removes a synthetic player.
     */
    void removePlayer(PlayerHandle player);

    /**
     * Waits for a number of ticks.
     */
    void waitTicks(int ticks);

    /**
     * Waits for a framework condition.
     */
    void waitUntil(Condition condition, Duration timeout);

    /**
     * Gets received message history for a player.
     */
    List<String> playerMessages(UUID playerId);
}
