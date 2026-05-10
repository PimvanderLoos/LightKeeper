package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dynamically captures Bukkit events by registering temporary listeners.
 */
final class AgentEventCapture
{
    private final JavaPlugin plugin;
    private final Map<String, List<Map<String, String>>> capturedEvents = new ConcurrentHashMap<>();
    private final Map<String, Listener> activeListeners = new ConcurrentHashMap<>();

    AgentEventCapture(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    void registerListener(String eventClassName)
        throws ClassNotFoundException
    {
        if (activeListeners.containsKey(eventClassName))
            return;

        final Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(eventClassName);
        final Listener listener = new Listener() {};

        Bukkit.getPluginManager().registerEvent(
            eventClass,
            listener,
            EventPriority.MONITOR,
            (l, event) -> captureEvent(event),
            plugin,
            true
        );

        activeListeners.put(eventClassName, listener);
        capturedEvents.putIfAbsent(eventClassName, new CopyOnWriteArrayList<>());
    }

    void unregisterListener(String eventClassName)
    {
        final Listener listener = activeListeners.remove(eventClassName);
        if (listener != null)
            HandlerList.unregisterAll(listener);
        capturedEvents.remove(eventClassName);
    }

    List<Map<String, String>> getCapturedEvents(String eventClassName)
    {
        final List<Map<String, String>> events = capturedEvents.get(eventClassName);
        return events != null ? new ArrayList<>(events) : Collections.emptyList();
    }

    void clearCapturedEvents(String eventClassName)
    {
        final List<Map<String, String>> events = capturedEvents.get(eventClassName);
        if (events != null)
            events.clear();
    }

    private void captureEvent(Event event)
    {
        // Check for the class name and its superclasses (e.g. if we listen for PlayerEvent but get PlayerMoveEvent)
        // Actually, Bukkit's registerEvent handles the class hierarchy for us.
        // But capturedEvents is keyed by the registered class name.
        
        for (final Map.Entry<String, List<Map<String, String>>> entry : capturedEvents.entrySet())
        {
            try
            {
                final Class<?> registeredClass = Class.forName(entry.getKey());
                if (registeredClass.isInstance(event))
                {
                    captureEventForList(event, entry.getValue());
                }
            }
            catch (ClassNotFoundException ignored)
            {
                // Ignored
            }
        }
    }

    private void captureEventForList(Event event, List<Map<String, String>> targetList)
    {
        final Map<String, String> data = new ConcurrentHashMap<>();
        for (final Method method : event.getClass().getMethods())
        {
            if (method.getParameterCount() == 0 &&
                (method.getName().startsWith("get") || method.getName().startsWith("is")) &&
                !method.getName().equals("getClass") &&
                !method.getName().equals("getHandlers") &&
                !method.getName().equals("getEventName"))
            {
                try
                {
                    final Object value = method.invoke(event);
                    if (isPrintable(value))
                        data.put(method.getName(), String.valueOf(value));
                }
                catch (Exception ignored)
                {
                    // Ignored
                }
            }
        }
        targetList.add(data);
    }

    private boolean isPrintable(Object value)
    {
        if (value == null) return false;
        final Class<?> clazz = value.getClass();
        return clazz.isPrimitive() ||
               value instanceof String ||
               value instanceof Number ||
               value instanceof Boolean ||
               value instanceof java.util.UUID ||
               clazz.isEnum();
    }
}
