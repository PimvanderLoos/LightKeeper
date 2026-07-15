package nl.pim16aap2.lightkeeper.framework;

/**
 * Events facet of the framework: capture Bukkit events for later inspection.
 *
 * <p>Obtained from {@link ILightkeeperFramework#events()}. Thin today; it grows as the event-driven APIs expand.
 */
public interface IEvents
{
    /**
     * Starts capturing Bukkit events of the specified type.
     *
     * @param eventClassName
     *     The full class name of the event to capture (e.g. "org.bukkit.event.player.PlayerMoveEvent").
     * @return A handle to manage the capture session.
     */
    EventCaptureHandle capture(String eventClassName);
}
