package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.GetCapturedEvents;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
     * Owning plugin used as registration context for Bukkit listeners.
     */
    private final JavaPlugin plugin;
    /**
     * Main-thread execution bridge used for Bukkit-safe (un)registration calls.
     */
    private final AgentMainThreadExecutor mainThreadExecutor;
    /**
     * Encoder turning fired events into typed wire payloads; owns the drop markers and their WARN-once logging.
     */
    private final ProtocolValueEncoder protocolValueEncoder;
    /**
     * Shared monotonic server-tick counter, read (thread-safely) at capture time to stamp events.
     */
    private final AtomicLong tickCounter;
    /**
     * Captured event payloads keyed by fully qualified event class name.
     */
    private final Map<String, List<GetCapturedEvents.CapturedEvent>> capturedEvents = new ConcurrentHashMap<>();
    /**
     * Active marker listeners keyed by fully qualified event class name.
     */
    private final Map<String, Listener> activeListeners = new ConcurrentHashMap<>();
    /**
     * Active cancel-next marker listeners keyed by fully qualified event class name. Kept separate from the
     * MONITOR capture listeners: cancellation acts at LOWEST priority so the capture listener observes the
     * final cancelled state.
     */
    private final Map<String, CancelNextState> cancelListeners = new ConcurrentHashMap<>();
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
    AgentEventCapture(JavaPlugin plugin, AgentMainThreadExecutor mainThreadExecutor, AtomicLong tickCounter)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.tickCounter = Objects.requireNonNull(tickCounter, "tickCounter");
        this.protocolValueEncoder = new ProtocolValueEncoder(plugin);
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
            final List<GetCapturedEvents.CapturedEvent> events =
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
        // Closing a capture also disarms any pending cancellation for the class: a leftover LOWEST-priority
        // cancel listener would silently cancel later tests' events on the shared server.
        final CancelNextState armedCancellation = cancelListeners.remove(eventClassName);
        if (armedCancellation != null)
        {
            // Zero the budget immediately: the marker stays registered until the scheduled unregister runs,
            // and events fired in that window must not be cancelled after close() promised a disarm.
            armedCancellation.remaining().set(0);
            Bukkit.getScheduler().runTask(plugin, () -> HandlerList.unregisterAll(armedCancellation.marker()));
        }
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
    List<GetCapturedEvents.CapturedEvent> getCapturedEvents(String eventClassName)
    {
        final List<GetCapturedEvents.CapturedEvent> events = capturedEvents.get(eventClassName);
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
        final List<GetCapturedEvents.CapturedEvent> events = capturedEvents.get(eventClassName);
        if (events == null)
            throw new IllegalArgumentException(
                "No capture listener is registered for event class '%s'; register it before clearing events."
                    .formatted(eventClassName));
        events.clear();
    }

    private void captureEventForList(Event event, List<GetCapturedEvents.CapturedEvent> targetList)
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

        targetList.add(new GetCapturedEvents.CapturedEvent(
            tickCounter.get(),
            protocolValueEncoder.encodeAccessors(event, event.getClass().getName())));
    }

    /**
     * Arms cancellation of the next {@code count} fired events of the class via a LOWEST-priority listener,
     * so regular plugin listeners (and the MONITOR capture listener) observe the cancelled state.
     *
     * <p>One armed cancellation per event class at a time; arming again while one is active throws. The
     * listener unregisters itself (on the next tick) once its count is exhausted.
     *
     * @param eventClassName
     *     Fully qualified Bukkit event class; must implement {@code Cancellable}.
     * @param count
     *     How many upcoming events to cancel.
     * @throws ClassNotFoundException
     *     When the class cannot be resolved by any reachable class loader.
     */
    void cancelNextEvents(String eventClassName, int count)
        throws ClassNotFoundException
    {
        final Class<?> resolvedClass = resolveEventClass(eventClassName);
        if (!Event.class.isAssignableFrom(resolvedClass))
            throw new IllegalArgumentException("Class '%s' is not a Bukkit Event.".formatted(eventClassName));
        if (!org.bukkit.event.Cancellable.class.isAssignableFrom(resolvedClass))
            throw new IllegalArgumentException(
                "Class '%s' is not Cancellable; cancelNext cannot apply.".formatted(eventClassName));

        final CancelNextState state = new CancelNextState(new Listener() {}, new AtomicInteger(count));
        final CancelNextState previous = cancelListeners.putIfAbsent(eventClassName, state);
        if (previous != null)
            throw new IllegalStateException(
                "A cancelNext for '%s' is already armed with %d remaining."
                    .formatted(eventClassName, previous.remaining().get()));

        boolean registered = false;
        try
        {
            final Class<? extends Event> eventClass = resolvedClass.asSubclass(Event.class);
            mainThreadExecutor.callOnMainThread(() ->
            {
                Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    state.marker(),
                    EventPriority.LOWEST,
                    (listenerInstance, event) -> cancelEvent(eventClassName, state, event),
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
                "Failed to register the cancelNext listener for '%s' on the main thread."
                    .formatted(eventClassName), exception);
        }
        finally
        {
            if (!registered)
                cancelListeners.remove(eventClassName, state);
        }
    }

    private void cancelEvent(String eventClassName, CancelNextState state, Event event)
    {
        final int before = state.remaining().getAndDecrement();
        if (before <= 0)
            return;
        if (event instanceof org.bukkit.event.Cancellable cancellable)
            cancellable.setCancelled(true);
        if (before == 1)
        {
            // Exhausted: detach on the next tick (unregistering mid-dispatch from inside the handler is
            // avoided deliberately), and drop the bookkeeping so a new cancelNext can be armed.
            cancelListeners.remove(eventClassName, state);
            Bukkit.getScheduler().runTask(plugin, () -> HandlerList.unregisterAll(state.marker()));
        }
    }

    /**
     * Bookkeeping for one armed cancellation: its marker listener and the remaining cancel budget.
     */
    private record CancelNextState(Listener marker, AtomicInteger remaining)
    {
    }
}
