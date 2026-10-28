package nl.pim16aap2.lightkeeper.framework;

import java.util.List;
import java.util.Objects;

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
    /** Validates the title and defensively copies the observed items. */
    public MenuSnapshot
    {
        Objects.requireNonNull(title, "title may not be null.");
        items = List.copyOf(Objects.requireNonNull(items, "items may not be null."));
    }
}
