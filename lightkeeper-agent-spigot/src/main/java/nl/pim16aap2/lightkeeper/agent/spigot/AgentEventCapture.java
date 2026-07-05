package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

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
     * Maximum number of events retained per registered event class name.
     * Prevents unbounded memory growth when a test forgets to close the capture handle.
     */
    private static final int MAX_CAPTURED_EVENTS_PER_CLASS = 10_000;
    /**
     * Maximum number of getter/isser methods inspected per event instance during capture.
     * Bounds reflection overhead for events with very wide APIs.
     */
    private static final int MAX_CAPTURE_METHODS_PER_EVENT = 32;

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
     * {@code eventClassName#methodName} keys whose capture failure has already been logged, so a getter that
     * throws on every event is warned about once rather than flooding the log up to the capture cap.
     */
    private final Set<String> loggedCaptureFailures = ConcurrentHashMap.newKeySet();
    /**
     * Event class names whose capture cap has already been warned about, so hitting the cap is reported once
     * rather than silently discarding every subsequent event.
     */
    private final Set<String> cappedEventClasses = ConcurrentHashMap.newKeySet();

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
     * Returns a snapshot of captured event payloads.
     *
     * @param eventClassName
     *     Fully qualified event class whose captured events should be returned.
     * @return
     *     Snapshot list of captured event payloads, or an empty list when nothing has been captured.
     */
    List<Map<String, String>> getCapturedEvents(String eventClassName)
    {
        final List<Map<String, String>> events = capturedEvents.get(eventClassName);
        if (events == null)
            throw new IllegalArgumentException(
                ("No capture listener is registered for event class '%s'; register it before querying captured "
                    + "events (a typo'd class name or a query after unregister otherwise looks like 'no events').")
                    .formatted(eventClassName));
        return new ArrayList<>(events);
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
        if (events == null)
            throw new IllegalArgumentException(
                "No capture listener is registered for event class '%s'; register it before clearing events."
                    .formatted(eventClassName));
        events.clear();
    }

    private void captureEventForList(Event event, List<Map<String, String>> targetList)
    {
        if (targetList.size() >= MAX_CAPTURED_EVENTS_PER_CLASS)
        {
            if (cappedEventClasses.add(event.getClass().getName()))
                plugin.getLogger().log(
                    Level.WARNING,
                    "Reached the capture cap of %d events for '%s'; further events are discarded until cleared."
                        .formatted(MAX_CAPTURED_EVENTS_PER_CLASS, event.getClass().getName()));
            return;
        }

        final Map<String, String> data = new ConcurrentHashMap<>();
        int inspectedMethodCount = 0;
        for (final Method method : event.getClass().getMethods())
        {
            if (inspectedMethodCount >= MAX_CAPTURE_METHODS_PER_EVENT)
                break;

            if (method.getParameterCount() == 0 &&
                (method.getName().startsWith("get") || method.getName().startsWith("is")) &&
                !method.getName().equals("getClass") &&
                !method.getName().equals("getHandlers") &&
                !method.getName().equals("getEventName"))
            {
                ++inspectedMethodCount;
                try
                {
                    final Object value = method.invoke(event);
                    if (isPrintable(value))
                        data.put(method.getName(), String.valueOf(value));
                }
                catch (InvocationTargetException exception)
                {
                    recordCaptureFailure(
                        data, event, method, exception.getCause() == null ? exception : exception.getCause());
                }
                catch (ReflectiveOperationException | RuntimeException exception)
                {
                    recordCaptureFailure(data, event, method, exception);
                }
            }
        }
        targetList.add(data);
    }

    /**
     * Records a visible sentinel for a failed event getter and warns once per event-class/method, so a getter
     * that throws is never dropped silently (an absent key would let a negative assertion pass on broken
     * capture).
     *
     * @param data
     *     Captured property map for the current event.
     * @param event
     *     The event whose property failed to capture.
     * @param method
     *     The getter that failed.
     * @param cause
     *     The underlying failure (already unwrapped from any {@link InvocationTargetException}).
     */
    private void recordCaptureFailure(Map<String, String> data, Event event, Method method, Throwable cause)
    {
        data.put(method.getName(), "<capture-failed: " + cause.getClass().getSimpleName() + ">");
        if (loggedCaptureFailures.add(event.getClass().getName() + "#" + method.getName()))
            plugin.getLogger().log(
                Level.WARNING,
                "Failed to capture property '%s' of event '%s'."
                    .formatted(method.getName(), event.getClass().getName()),
                cause
            );
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
