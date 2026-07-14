package nl.pim16aap2.lightkeeper.framework;

/**
 * Outcome of a synthetic player interaction that fires a real, cancellable Bukkit event.
 *
 * @param eventFired
 *     Whether a real Bukkit event was fired for the interaction. Block clicks always fire a
 *     {@code PlayerInteractEvent}; bypass-style operations (e.g. direct block sets) never do.
 * @param cancelled
 *     Whether a plugin cancelled the fired event. Always {@code false} when no event was fired.
 */
public record InteractionResult(
    boolean eventFired,
    boolean cancelled
)
{
}
