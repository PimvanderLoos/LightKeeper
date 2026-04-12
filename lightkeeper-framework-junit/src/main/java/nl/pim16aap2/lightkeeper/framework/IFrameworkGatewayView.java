package nl.pim16aap2.lightkeeper.framework;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Internal gateway contract used by framework handle implementations.
 * <p>
 * This type is not part of the supported public API surface and exists to decouple public handle types from
 * implementation classes in {@code framework.internal}.
 */
public interface IFrameworkGatewayView
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
     * Fires a left-click block interaction as a synthetic player.
     */
    void leftClickBlock(UUID playerId, Vector3Di position, String blockFace);

    /**
     * Fires a right-click block interaction as a synthetic player.
     */
    void rightClickBlock(UUID playerId, Vector3Di position, String blockFace);

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
