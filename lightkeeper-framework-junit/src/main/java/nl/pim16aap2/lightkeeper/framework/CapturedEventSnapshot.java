package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshot of a captured Bukkit event.
 *
 * <p>Values arrive as typed {@link IProtocolValue}s: numbers, booleans, UUIDs, and enum constants keep their
 * types, identifiable objects (entities, worlds) travel as {@link IProtocolValue.PRef references}, nested
 * objects as {@link IProtocolValue.PRecord records}, and anything the agent could not encode appears as a
 * {@link IProtocolValue.PDropped} marker instead of being silently absent.
 *
 * @param eventClassName
 *     The full class name of the event.
 * @param values
 *     The captured event values keyed by accessor name (getter/is-method or record component).
 */
public record CapturedEventSnapshot(
    String eventClassName,
    Map<String, IProtocolValue> values
)
{
    /**
     * Validates and defensively copies the values, preserving their order.
     */
    public CapturedEventSnapshot
    {
        values = values == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Gets a single captured value by accessor name.
     *
     * @param accessorName
     *     The accessor name, e.g. {@code "getAction"} or {@code "isCancelled"}.
     * @return The captured value, or {@code null} when the accessor returned null or was not captured.
     */
    public @Nullable IProtocolValue value(String accessorName)
    {
        return values.get(accessorName);
    }

    /**
     * Gets the captured values rendered as display text.
     *
     * @return Accessor name to rendered value, in capture order.
     * @deprecated Use {@link #values()} (typed) or {@link #value(String)} instead; this string view only
     *     remains so existing tests keep compiling for one release.
     */
    @Deprecated(forRemoval = true)
    public Map<String, String> data()
    {
        final Map<String, String> rendered = new LinkedHashMap<>();
        values.forEach((accessorName, value) -> rendered.put(accessorName, value.toDisplayString()));
        return Collections.unmodifiableMap(rendered);
    }
}
