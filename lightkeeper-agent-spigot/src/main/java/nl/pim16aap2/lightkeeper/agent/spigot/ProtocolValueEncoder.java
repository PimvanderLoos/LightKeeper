package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
            for (final RecordComponent component : boundedRecordComponents(subject))
            {
                final IProtocolValue value =
                    encodeAccessorResult(subject, component.getAccessor(), component.getName(),
                        context, remainingDepth);
                if (value != null)
                    encoded.put(component.getName(), value);
            }
            return encoded;
        }

        for (final Method accessor : boundedAccessors(subject))
        {
            final IProtocolValue value =
                encodeAccessorResult(subject, accessor, accessor.getName(), context, remainingDepth);
            if (value != null)
                encoded.put(accessor.getName(), value);
        }
        return encoded;
    }

    private List<RecordComponent> boundedRecordComponents(Object subject)
    {
        final RecordComponent[] components = subject.getClass().getRecordComponents();
        return Arrays.asList(components).subList(0, Math.min(components.length, MAX_ACCESSORS_PER_OBJECT));
    }

    private List<Method> boundedAccessors(Object subject)
    {
        return Arrays.stream(subject.getClass().getMethods())
            .filter(method -> method.getParameterCount() == 0)
            .filter(method -> method.getName().startsWith("get") || method.getName().startsWith("is"))
            .filter(method -> !method.getName().equals("getClass")
                && !method.getName().equals("getHandlers")
                && !method.getName().equals("getEventName"))
            .sorted(Comparator.comparing(Method::getName))
            .limit(MAX_ACCESSORS_PER_OBJECT)
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
        if (value.getClass().isEnum())
            return new IProtocolValue.PEnum(value.getClass().getName(), ((Enum<?>) value).name());
        if (value instanceof Entity entity)
            return new IProtocolValue.PRef(entity.getClass().getName(), entity.getUniqueId().toString());
        if (value instanceof World world)
            return new IProtocolValue.PRef(world.getClass().getName(), world.getName());

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
        final List<IProtocolValue> elements = new ArrayList<>(collection.size());
        for (final Object element : collection)
        {
            if (element == null)
                continue;
            elements.add(encodeValue(element, accessorName, context, remainingDepth - 1));
        }
        return new IProtocolValue.PList(elements);
    }

    private IProtocolValue encodeArray(Object array, String accessorName, String context, int remainingDepth)
    {
        final int length = Array.getLength(array);
        final List<IProtocolValue> elements = new ArrayList<>(length);
        for (int i = 0; i < length; i++)
        {
            final Object element = Array.get(array, i);
            if (element == null)
                continue;
            elements.add(encodeValue(element, accessorName, context, remainingDepth - 1));
        }
        return new IProtocolValue.PList(elements);
    }

    private IProtocolValue encodeMap(Map<?, ?> map, String accessorName, String context, int remainingDepth)
    {
        final Map<String, IProtocolValue> fields = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : map.entrySet())
        {
            if (entry.getValue() == null)
                continue;
            fields.put(String.valueOf(entry.getKey()),
                encodeValue(entry.getValue(), accessorName, context, remainingDepth - 1));
        }
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
