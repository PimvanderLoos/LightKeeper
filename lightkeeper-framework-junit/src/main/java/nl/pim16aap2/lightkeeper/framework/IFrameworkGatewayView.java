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
     * Grants a permission node to a synthetic player by setting it to {@code true} on the player's attachment.
     */
    void grantPermission(UUID playerId, String permission);

    /**
     * Revokes a permission node from a synthetic player by setting it to {@code false} on the player's attachment.
     */
    void revokePermission(UUID playerId, String permission);

    /**
     * Removes a permission node from a synthetic player's attachment, restoring the player's default.
     */
    void unsetPermission(UUID playerId, String permission);

    /**
     * Queries the live value of a permission node for a synthetic player.
     */
    boolean hasPermission(UUID playerId, String permission);

    /**
     * Teleports a synthetic player.
     *
     * @return True if the teleport succeeded, false if it was blocked (e.g. a cancelled teleport event).
     */
    boolean teleportPlayer(UUID uuid, String worldName, double x, double y, double z);

    /**
     * Places a block as a synthetic player.
     */
    void placePlayerBlock(UUID playerId, String material, int x, int y, int z);

    /**
     * Loads a chunk.
     *
     * @return True if the chunk was loaded successfully.
     */
    boolean loadChunk(String worldName, int x, int z);

    /**
     * Unloads a chunk.
     */
    boolean unloadChunk(String worldName, int x, int z);

    /**
     * Checks if a chunk is loaded.
     */
    boolean isChunkLoaded(String worldName, int x, int z);

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

    /**
     * Gets captured chat components for a player.
     */
    List<ChatComponentSnapshot> playerChatComponents(UUID playerId);

    /**
     * Gets player inventory snapshot.
     */
    InventorySnapshot playerInventory(UUID playerId);

    /**
     * Drops item from player's main hand.
     */
    boolean dropItem(UUID playerId);

    /**
     * Registers an event listener.
     */
    void registerEventListener(String eventClassName);

    /**
     * Gets captured events for a class.
     */
    List<CapturedEventSnapshot> getCapturedEvents(String eventClassName);

    /**
     * Clears captured events for a class.
     */
    void clearCapturedEvents(String eventClassName);

    /**
     * Unregisters an event listener.
     */
    void unregisterEventListener(String eventClassName);

    /**
     * Gets all captured server errors: structured log events plus raw stderr stack-trace detections.
     */
    List<ServerErrorSnapshot> capturedServerErrors();

    /**
     * Clears all captured server errors and advances the raw stderr scan window.
     */
    void clearServerErrors();
}
