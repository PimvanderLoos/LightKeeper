package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dynamically captures Bukkit events by registering temporary listeners.
 *
 * <p>All Bukkit listener (un)registration calls are routed through {@link AgentMainThreadExecutor} because
 * {@link org.bukkit.plugin.PluginManager#registerEvent} and {@link HandlerList#unregisterAll(Listener)} must
 * run on the Bukkit primary thread.
 */
final class AgentEventCapture
{
    /**
     * Owning plugin used as registration context for Bukkit listeners.
     */
    private final JavaPlugin plugin;
    /**
     * Main-thread execution bridge used for Bukkit-safe (un)registration calls.
     */
    private final AgentMainThreadExecutor mainThreadExecutor;
    /**
     * Captured event payloads keyed by fully qualified event class name.
     */
    private final Map<String, List<Map<String, String>>> capturedEvents = new ConcurrentHashMap<>();
    /**
     * Active marker listeners keyed by fully qualified event class name.
     */
    private final Map<String, Listener> activeListeners = new ConcurrentHashMap<>();

    /**
     * @param plugin
     *     Owning plugin used as Bukkit registration context.
     * @param mainThreadExecutor
     *     Main-thread executor used to route Bukkit listener (un)registration onto the server thread.
     */
    AgentEventCapture(JavaPlugin plugin, AgentMainThreadExecutor mainThreadExecutor)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
    }

    /**
     * Registers a monitor-priority listener that captures every fired event of the requested type.
     *
     * <p>Resolution and Bukkit registration are atomic per event class name: concurrent invocations for the
     * same class register the listener exactly once.
     *
     * @param eventClassName
     *     Fully qualified Bukkit event class to register.
     * @throws ClassNotFoundException
     *     When the class cannot be resolved by any reachable class loader.
     */
    void registerListener(String eventClassName)
        throws ClassNotFoundException
    {
        final Listener marker = new Listener() {};
        final Listener previous = activeListeners.putIfAbsent(eventClassName, marker);
        if (previous != null)
            return;

        boolean registered = false;
        try
        {
            final Class<?> resolvedClass = resolveEventClass(eventClassName);
            if (!Event.class.isAssignableFrom(resolvedClass))
                throw new IllegalArgumentException("Class '%s' is not a Bukkit Event.".formatted(eventClassName));

            final Class<? extends Event> eventClass = resolvedClass.asSubclass(Event.class);
            final List<Map<String, String>> events =
                capturedEvents.computeIfAbsent(eventClassName, ignored -> new CopyOnWriteArrayList<>());

            mainThreadExecutor.callOnMainThread(() ->
            {
                Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    marker,
                    EventPriority.MONITOR,
                    (listenerInstance, event) -> captureEventForList(event, events),
                    plugin,
                    false
                );
                return Boolean.TRUE;
            });
            registered = true;
        }
        catch (ClassNotFoundException | RuntimeException exception)
        {
            throw exception;
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(
                "Failed to register listener for '%s' on the main thread.".formatted(eventClassName), exception);
        }
        finally
        {
            if (!registered)
                activeListeners.remove(eventClassName, marker);
        }
    }

    private Class<?> resolveEventClass(String eventClassName)
        throws ClassNotFoundException
    {
        final Set<ClassLoader> classLoaders = new LinkedHashSet<>();
        classLoaders.add(AgentEventCapture.class.getClassLoader());
        classLoaders.add(plugin.getClass().getClassLoader());
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null)
            classLoaders.add(contextClassLoader);

        ClassNotFoundException failure = null;
        for (final ClassLoader classLoader : classLoaders)
        {
            try
            {
                return Class.forName(eventClassName, false, classLoader);
            }
            catch (ClassNotFoundException exception)
            {
                if (failure == null)
                    failure = exception;
                else
                    failure.addSuppressed(exception);
            }
        }

        for (final Plugin registeredPlugin : Bukkit.getPluginManager().getPlugins())
        {
            try
            {
                return Class.forName(eventClassName, false, registeredPlugin.getClass().getClassLoader());
            }
            catch (ClassNotFoundException exception)
            {
                if (failure == null)
                    failure = exception;
                else
                    failure.addSuppressed(exception);
            }
        }

        if (failure != null)
            throw failure;
        throw new ClassNotFoundException(eventClassName);
    }

    /**
     * Unregisters a previously registered capture listener and discards its accumulated events.
     *
     * @param eventClassName
     *     Fully qualified event class previously passed to {@link #registerListener(String)}.
     */
    void unregisterListener(String eventClassName)
    {
        final Listener listener = activeListeners.remove(eventClassName);
        capturedEvents.remove(eventClassName);
        if (listener == null)
            return;

        try
        {
            mainThreadExecutor.callOnMainThread(() ->
            {
                HandlerList.unregisterAll(listener);
                return Boolean.TRUE;
            });
        }
        catch (RuntimeException exception)
        {
            throw exception;
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(
                "Failed to unregister listener for '%s' on the main thread.".formatted(eventClassName), exception);
        }
    }

    /**
     * @param eventClassName
     *     Fully qualified event class whose captured events should be returned.
     * @return
     *     Snapshot list of captured event payloads, or an empty list when nothing has been captured.
     */
    List<Map<String, String>> getCapturedEvents(String eventClassName)
    {
        final List<Map<String, String>> events = capturedEvents.get(eventClassName);
        return events != null ? new ArrayList<>(events) : Collections.emptyList();
    }

    /**
     * Discards all previously captured events for the requested event class without unregistering its listener.
     *
     * @param eventClassName
     *     Fully qualified event class whose captured events should be cleared.
     */
    void clearCapturedEvents(String eventClassName)
    {
        final List<Map<String, String>> events = capturedEvents.get(eventClassName);
        if (events != null)
            events.clear();
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
        return value instanceof String ||
               value instanceof Number ||
               value instanceof Boolean ||
               value instanceof java.util.UUID ||
               value.getClass().isEnum();
    }
}
