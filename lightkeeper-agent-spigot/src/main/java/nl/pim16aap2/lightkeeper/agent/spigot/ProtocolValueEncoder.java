package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Generic reflective encoder from live Java objects to the {@link IProtocolValue} wire envelope.
 *
 * <p>Simple values map to their leaves (text, numbers, booleans, UUIDs, enum constants); identifiable Bukkit
 * objects travel as references ({@link IProtocolValue.PRef}: entities by UUID, worlds by name) instead of being
 * walked; collections, maps, and other objects nest ({@link IProtocolValue.PList},
 * {@link IProtocolValue.PRecord}) up to a bounded depth. Records are walked through their record components, all
 * other objects through their public zero-arg {@code get*}/{@code is*} accessors (sorted by name for
 * deterministic output, capped per object).
 *
 * <p>Nothing is skipped silently: a value the encoder cannot represent — depth exhausted, an unencodable type,
 * or a throwing accessor — is reported as a {@link IProtocolValue.PDropped} marker, with a WARN logged once per
 * (context, accessor) pair. Only {@code null} values are omitted entirely: an absent value is data, not data
 * loss.
 */
final class ProtocolValueEncoder
{
    /**
     * Maximum nesting depth for encoded object graphs; deeper values are reported as dropped.
     */
    static final int MAX_DEPTH = 3;

    /**
     * Maximum number of accessors encoded per object; prevents pathological types from bloating payloads.
     */
    static final int MAX_ACCESSORS_PER_OBJECT = 32;

    /**
     * Maximum number of elements encoded per collection, array, or map; a truncated container carries a
     * trailing {@link IProtocolValue.PDropped} marker naming the omitted remainder.
     */
    static final int MAX_CONTAINER_ELEMENTS = 128;

    /**
     * Reserved field name for truncation markers in encoded records and maps.
     */
    static final String TRUNCATED_KEY = "<truncated>";

    private final JavaPlugin plugin;
    /**
     * WARN-once guard keyed by {@code context + "#" + accessorName}, so a noisy accessor logs a single warning
     * regardless of how many times its value is dropped.
     */
    private final Set<String> loggedDrops = ConcurrentHashMap.newKeySet();

    /**
     * Creates an encoder that logs drop warnings through the given plugin's logger.
     *
     * @param plugin
     *     Owning plugin used for logging.
     */
    ProtocolValueEncoder(JavaPlugin plugin)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Encodes an object's accessible state into named {@link IProtocolValue}s.
     *
     * @param subject
     *     The object whose accessors to walk.
     * @param context
     *     Context label for drop warnings, e.g. the captured event's class name.
     * @return Accessor name to encoded value, in sorted accessor order; {@code null}-valued accessors are
     *     omitted.
     */
    Map<String, IProtocolValue> encodeAccessors(Object subject, String context)
    {
        return walkAccessors(subject, context, MAX_DEPTH);
    }

    private Map<String, IProtocolValue> walkAccessors(Object subject, String context, int remainingDepth)
    {
        final Map<String, IProtocolValue> encoded = new LinkedHashMap<>();
        if (subject.getClass().isRecord())
        {
            final RecordComponent[] components = subject.getClass().getRecordComponents();
            for (int i = 0; i < Math.min(components.length, MAX_ACCESSORS_PER_OBJECT); i++)
            {
                final RecordComponent component = components[i];
                final IProtocolValue value =
                    encodeAccessorResult(subject, component.getAccessor(), component.getName(),
                        context, remainingDepth);
                if (value != null)
                    encoded.put(component.getName(), value);
            }
            markTruncatedAccessors(encoded, context, components.length);
            return encoded;
        }

        final List<Method> accessors = sortedInstanceAccessors(subject);
        for (int i = 0; i < Math.min(accessors.size(), MAX_ACCESSORS_PER_OBJECT); i++)
        {
            final Method accessor = accessors.get(i);
            final IProtocolValue value =
                encodeAccessorResult(subject, accessor, accessor.getName(), context, remainingDepth);
            if (value != null)
                encoded.put(accessor.getName(), value);
        }
        markTruncatedAccessors(encoded, context, accessors.size());
        return encoded;
    }

    /**
     * Appends a loud truncation marker when an object exposed more accessors than the per-object cap, so the
     * cap never silently discards fields.
     */
    private void markTruncatedAccessors(Map<String, IProtocolValue> encoded, String context, int totalAccessors)
    {
        if (totalAccessors > MAX_ACCESSORS_PER_OBJECT)
            encoded.put(TRUNCATED_KEY, dropped(context, TRUNCATED_KEY,
                "truncated: %d more accessors".formatted(totalAccessors - MAX_ACCESSORS_PER_OBJECT)));
    }

    private List<Method> sortedInstanceAccessors(Object subject)
    {
        return Arrays.stream(subject.getClass().getMethods())
            // Static methods are never instance state; without this, every Bukkit event's static
            // getHandlerList() would drag server-global handler registrations into each payload.
            .filter(method -> !Modifier.isStatic(method.getModifiers()))
            .filter(method -> method.getParameterCount() == 0)
            .filter(method -> method.getName().startsWith("get") || method.getName().startsWith("is"))
            .filter(method -> !method.getName().equals("getClass")
                && !method.getName().equals("getHandlers")
                && !method.getName().equals("getEventName"))
            .sorted(Comparator.comparing(Method::getName))
            .toList();
    }

    private @Nullable IProtocolValue encodeAccessorResult(
        Object subject,
        Method accessor,
        String accessorName,
        String context,
        int remainingDepth)
    {
        final Object value;
        try
        {
            value = accessor.invoke(subject);
        }
        catch (InvocationTargetException exception)
        {
            final Throwable cause = Objects.requireNonNullElse(exception.getCause(), exception);
            return dropped(context, accessorName, "capture-failed: " + cause.getClass().getSimpleName());
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            return dropped(context, accessorName, "capture-failed: " + exception.getClass().getSimpleName());
        }

        if (value == null)
            return null;
        return encodeValue(value, accessorName, context, remainingDepth);
    }

    /**
     * Encodes a single non-null value at the given remaining depth.
     */
    private IProtocolValue encodeValue(Object value, String accessorName, String context, int remainingDepth)
    {
        if (value instanceof String string)
            return new IProtocolValue.PString(string);
        if (value instanceof Number number)
            return new IProtocolValue.PNumber(number);
        if (value instanceof Boolean bool)
            return new IProtocolValue.PBool(bool);
        if (value instanceof UUID uuid)
            return new IProtocolValue.PUuid(uuid);
        // instanceof (not Class#isEnum) so enum constants with class bodies (e.g. ChatColor.RED, a synthetic
        // ChatColor$13 subclass) still encode as enums; getDeclaringClass names the enum type, not the subclass.
        if (value instanceof Enum<?> enumValue)
            return new IProtocolValue.PEnum(enumValue.getDeclaringClass().getName(), enumValue.name());
        if (value instanceof Entity entity)
            return new IProtocolValue.PRef(entity.getClass().getName(), entity.getUniqueId().toString());
        if (value instanceof World world)
            return new IProtocolValue.PRef(world.getClass().getName(), world.getName());
        // Positions are identity-shaped leaves like refs: a Location's getChunk()/getBlock() accessors would
        // otherwise drag world geometry into the payload through the generic walk. The record is built by
        // hand (world ref + position) so cross-world events at equal coordinates stay distinguishable;
        // orientation is dropped. The world key is omitted for unbound/unloaded-world locations.
        if (value instanceof Location location)
            return encodeLocation(location);
        if (value instanceof Vector vector)
            return new IProtocolValue.PVec(vector.getX(), vector.getY(), vector.getZ());

        if (remainingDepth <= 0)
            return dropped(context, accessorName, value.getClass().getName());

        if (value instanceof Collection<?> collection)
            return encodeList(collection, accessorName, context, remainingDepth);
        if (value.getClass().isArray())
            return encodeArray(value, accessorName, context, remainingDepth);
        if (value instanceof Map<?, ?> map)
            return encodeMap(map, accessorName, context, remainingDepth);

        final Map<String, IProtocolValue> nested = walkAccessors(value, context, remainingDepth - 1);
        if (nested.isEmpty())
            return dropped(context, accessorName, value.getClass().getName());
        return new IProtocolValue.PRecord(nested);
    }

    private IProtocolValue encodeList(
        Collection<?> collection,
        String accessorName,
        String context,
        int remainingDepth)
    {
        final List<IProtocolValue> elements = new ArrayList<>(Math.min(collection.size(), MAX_CONTAINER_ELEMENTS));
        int encodedCount = 0;
        for (final Object element : collection)
        {
            if (element == null)
                continue;
            if (encodedCount >= MAX_CONTAINER_ELEMENTS)
            {
                elements.add(truncated(context, accessorName, collection.size() - encodedCount));
                break;
            }
            elements.add(encodeValue(element, accessorName, context, remainingDepth - 1));
            encodedCount++;
        }
        return new IProtocolValue.PList(elements);
    }

    private IProtocolValue encodeArray(Object array, String accessorName, String context, int remainingDepth)
    {
        final int length = Array.getLength(array);
        final List<IProtocolValue> elements = new ArrayList<>(Math.min(length, MAX_CONTAINER_ELEMENTS));
        int encodedCount = 0;
        for (int i = 0; i < length; i++)
        {
            final Object element = Array.get(array, i);
            if (element == null)
                continue;
            if (encodedCount >= MAX_CONTAINER_ELEMENTS)
            {
                elements.add(truncated(context, accessorName, length - encodedCount));
                break;
            }
            elements.add(encodeValue(element, accessorName, context, remainingDepth - 1));
            encodedCount++;
        }
        return new IProtocolValue.PList(elements);
    }

    private IProtocolValue encodeMap(Map<?, ?> map, String accessorName, String context, int remainingDepth)
    {
        final Map<String, IProtocolValue> fields = new LinkedHashMap<>();
        int encodedCount = 0;
        for (final Map.Entry<?, ?> entry : map.entrySet())
        {
            if (entry.getValue() == null)
                continue;
            if (encodedCount >= MAX_CONTAINER_ELEMENTS)
            {
                fields.put(TRUNCATED_KEY, truncated(context, accessorName, map.size() - encodedCount));
                break;
            }
            final String key = String.valueOf(entry.getKey());
            final IProtocolValue encodedEntry =
                encodeValue(entry.getValue(), accessorName, context, remainingDepth - 1);
            // Distinct map keys can stringify identically (1 vs "1"); overwriting would lose data silently,
            // so a collision is reported as a loud marker instead.
            if (fields.putIfAbsent(key, encodedEntry) != null)
                fields.put(key, dropped(context, accessorName, "key-collision: '%s'".formatted(key)));
            encodedCount++;
        }
        return new IProtocolValue.PRecord(fields);
    }

    private IProtocolValue.PDropped truncated(String context, String accessorName, int omittedCount)
    {
        return dropped(context, accessorName, "truncated: %d more elements".formatted(omittedCount));
    }


    /**
     * Encodes a {@link Location} as a hand-built record of world identity plus position, never via the
     * generic accessor walk.
     */
    private static IProtocolValue.PRecord encodeLocation(Location location)
    {
        final LinkedHashMap<String, IProtocolValue> fields = new LinkedHashMap<>();
        // isWorldLoaded guards Location#getWorld's IllegalArgumentException for stale world references; the
        // null check covers unbound locations (and satisfies NullAway).
        if (location.isWorldLoaded())
        {
            final @Nullable World world = location.getWorld();
            if (world != null)
                fields.put("world", new IProtocolValue.PRef(world.getClass().getName(), world.getName()));
        }
        fields.put("position", new IProtocolValue.PVec(location.getX(), location.getY(), location.getZ()));
        return new IProtocolValue.PRecord(fields);
    }

    private IProtocolValue.PDropped dropped(String context, String accessorName, String runtimeType)
    {
        if (loggedDrops.add(context + "#" + accessorName))
            plugin.getLogger().log(
                Level.WARNING,
                () -> ("LK_AGENT: Dropped value of accessor '%s' (%s) while encoding '%s'; it will appear as a "
                    + "DROPPED marker in captured payloads.").formatted(accessorName, runtimeType, context)
            );
        return new IProtocolValue.PDropped(accessorName, runtimeType);
    }
}
