package nl.pim16aap2.lightkeeper.framework;

import java.util.List;

/**
 * Open-menu snapshot for a player.
 *
 * @param open
 *     Whether a menu is open.
 * @param title
 *     Menu title.
 * @param items
 *     Snapshot items.
 */
public record MenuSnapshot(
    boolean open,
    String title,
    List<MenuItemSnapshot> items
)
{
}
