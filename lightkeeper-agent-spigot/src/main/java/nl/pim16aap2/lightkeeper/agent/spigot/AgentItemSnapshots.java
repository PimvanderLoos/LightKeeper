package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.ItemSnapshot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Builds typed {@link ItemSnapshot} wire snapshots from live Bukkit {@link ItemStack}s, shared by the inventory
 * and menu handlers so both produce an identical slot representation.
 */
@SuppressWarnings("deprecation")
final class AgentItemSnapshots
{
    private AgentItemSnapshots()
    {
    }

    /**
     * Builds a snapshot for a non-air item at the given raw slot.
     *
     * @param slot
     *     Raw slot index.
     * @param item
     *     Non-air item stack.
     * @return Typed slot snapshot.
     */
    static ItemSnapshot of(int slot, ItemStack item)
    {
        final String displayName = item.getItemMeta() == null ? null : item.getItemMeta().getDisplayName();
        final List<String> lore = item.getItemMeta() == null
            ? List.of()
            : Objects.requireNonNullElse(item.getItemMeta().getLore(), List.<String>of());
        return new ItemSnapshot(slot, item.getType().getKey().toString(), displayName, lore);
    }
}
