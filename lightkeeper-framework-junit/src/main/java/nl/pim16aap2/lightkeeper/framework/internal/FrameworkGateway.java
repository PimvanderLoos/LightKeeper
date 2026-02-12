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
public interface FrameworkGateway
{
    String getBlock(String worldName, Vector3Di position);

    void setBlock(String worldName, Vector3Di position, String material);

    void executePlayerCommand(UUID playerId, String command);

    void placePlayerBlock(UUID playerId, String material, int x, int y, int z);

    MenuSnapshot menuSnapshot(UUID playerId);

    void clickMenuSlot(UUID playerId, int slot);

    void dragMenuSlots(UUID playerId, String materialKey, int... slots);

    void removePlayer(PlayerHandle player);

    void waitTicks(int ticks);

    void waitUntil(Condition condition, Duration timeout);

    List<String> playerMessages(UUID playerId);
}
