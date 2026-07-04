package nl.pim16aap2.lightkeeper.framework;

import java.util.Map;

/**
 * Snapshot of a captured Bukkit event.
 *
 * @param eventClassName
 *     The full class name of the event.
 * @param data
 *     The captured event data (getter/is-method results).
 */
public record CapturedEventSnapshot(
    String eventClassName,
    Map<String, String> data
)
{
}
