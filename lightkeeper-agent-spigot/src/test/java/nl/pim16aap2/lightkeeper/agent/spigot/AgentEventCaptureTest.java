package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEventCaptureTest
{
    @Test
    void registerListener_shouldCaptureCancelledEventsIntoRegisteredEventList()
        throws Exception
    {
        // setup
        final JavaPlugin plugin = mock();
        final PluginManager pluginManager = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin, new AgentMainThreadExecutor(plugin));
        final ArgumentCaptor<EventExecutor> executorCaptor = ArgumentCaptor.forClass(EventExecutor.class);

        // execute
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(pluginManager.getPlugins()).thenReturn(new Plugin[0]);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            eventCapture.registerListener(TestCaptureEvent.class.getName());
        }
        verify(pluginManager).registerEvent(
            eq(TestCaptureEvent.class),
            any(Listener.class),
            eq(EventPriority.MONITOR),
            executorCaptor.capture(),
            eq(plugin),
            eq(false)
        );
        executorCaptor.getValue().execute(mock(Listener.class), new TestCaptureEvent("value", true));

        // verify
        final List<Map<String, String>> events = eventCapture.getCapturedEvents(TestCaptureEvent.class.getName());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst())
            .containsEntry("getValue", "value")
            .containsEntry("isCancelled", "true");
    }

    @Test
    void registerListener_shouldRecordSentinelWhenEventGetterThrows()
        throws Exception
    {
        // setup
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        final PluginManager pluginManager = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin, new AgentMainThreadExecutor(plugin));
        final ArgumentCaptor<EventExecutor> executorCaptor = ArgumentCaptor.forClass(EventExecutor.class);

        // execute
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(pluginManager.getPlugins()).thenReturn(new Plugin[0]);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            eventCapture.registerListener(ThrowingGetterEvent.class.getName());
        }
        verify(pluginManager).registerEvent(
            eq(ThrowingGetterEvent.class),
            any(Listener.class),
            eq(EventPriority.MONITOR),
            executorCaptor.capture(),
            eq(plugin),
            eq(false)
        );
        executorCaptor.getValue().execute(mock(Listener.class), new ThrowingGetterEvent());

        // verify — a throwing getter must not be dropped silently; it is recorded as a visible sentinel
        final List<Map<String, String>> events =
            eventCapture.getCapturedEvents(ThrowingGetterEvent.class.getName());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().get("getBroken"))
            .isNotNull()
            .contains("capture-failed")
            .contains("IllegalStateException");
    }

    @Test
    void registerListener_shouldThrowExceptionWhenClassIsNotBukkitEvent()
    {
        // setup
        final JavaPlugin plugin = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin, new AgentMainThreadExecutor(plugin));
        final PluginManager pluginManager = mock();

        // execute + verify
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(pluginManager.getPlugins()).thenReturn(new Plugin[0]);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            assertThatThrownBy(() -> eventCapture.registerListener(String.class.getName()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a Bukkit Event");
        }
    }

    @Test
    void clearCapturedEvents_shouldRemoveCapturedData()
        throws Exception
    {
        // setup
        final JavaPlugin plugin = mock();
        final PluginManager pluginManager = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin, new AgentMainThreadExecutor(plugin));

        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(pluginManager.getPlugins()).thenReturn(new Plugin[0]);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            fireOneTestCaptureEvent(eventCapture, pluginManager, plugin);

            // execute
            eventCapture.clearCapturedEvents(TestCaptureEvent.class.getName());

            // verify
            assertThat(eventCapture.getCapturedEvents(TestCaptureEvent.class.getName())).isEmpty();
        }
    }

    @Test
    void unregisterListener_shouldRemoveCapturedData()
        throws Exception
    {
        // setup
        final JavaPlugin plugin = mock();
        final PluginManager pluginManager = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin, new AgentMainThreadExecutor(plugin));

        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(pluginManager.getPlugins()).thenReturn(new Plugin[0]);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            fireOneTestCaptureEvent(eventCapture, pluginManager, plugin);

            // execute
            eventCapture.unregisterListener(TestCaptureEvent.class.getName());

            // verify
            assertThat(eventCapture.getCapturedEvents(TestCaptureEvent.class.getName())).isEmpty();
        }
    }

    @Test
    void registerListener_shouldBeIdempotentWhenListenerIsAlreadyRegistered()
        throws Exception
    {
        // setup
        final JavaPlugin plugin = mock();
        final PluginManager pluginManager = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin, new AgentMainThreadExecutor(plugin));

        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(pluginManager.getPlugins()).thenReturn(new Plugin[0]);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            // execute
            eventCapture.registerListener(TestCaptureEvent.class.getName());
            eventCapture.registerListener(TestCaptureEvent.class.getName());
        }

        // verify: only one registerEvent call despite two registration attempts
        verify(pluginManager).registerEvent(
            eq(TestCaptureEvent.class),
            any(Listener.class),
            eq(EventPriority.MONITOR),
            any(EventExecutor.class),
            eq(plugin),
            eq(false)
        );
    }

    @Test
    void getCapturedEvents_shouldReturnEmptyListForUnregisteredClass()
    {
        // setup
        final JavaPlugin plugin = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin, new AgentMainThreadExecutor(plugin));

        // execute
        final List<Map<String, String>> events = eventCapture.getCapturedEvents("com.example.NonExistentEvent");

        // verify
        assertThat(events).isEmpty();
    }

    @Test
    void registerListener_shouldThrowExceptionWhenClassIsNotFound()
    {
        // setup
        final JavaPlugin plugin = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin, new AgentMainThreadExecutor(plugin));
        final PluginManager pluginManager = mock();

        // execute + verify
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(pluginManager.getPlugins()).thenReturn(new Plugin[0]);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            assertThatThrownBy(() -> eventCapture.registerListener("com.example.NonExistentEvent"))
                .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void captureEventForList_shouldSkipNullAndNonPrintableFieldValues()
        throws Exception
    {
        // setup
        final JavaPlugin plugin = mock();
        final PluginManager pluginManager = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin, new AgentMainThreadExecutor(plugin));
        final ArgumentCaptor<EventExecutor> executorCaptor = ArgumentCaptor.forClass(EventExecutor.class);

        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(pluginManager.getPlugins()).thenReturn(new Plugin[0]);
            bukkitMockedStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            eventCapture.registerListener(MixedFieldsEvent.class.getName());
        }
        verify(pluginManager).registerEvent(
            eq(MixedFieldsEvent.class),
            any(Listener.class),
            eq(EventPriority.MONITOR),
            executorCaptor.capture(),
            eq(plugin),
            eq(false)
        );

        // execute - fire event with string (printable) and list (not printable)
        executorCaptor.getValue().execute(mock(Listener.class), new MixedFieldsEvent("hello", null));

        // verify - only the string field should be captured
        final List<Map<String, String>> events = eventCapture.getCapturedEvents(MixedFieldsEvent.class.getName());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).containsKey("getLabel");
        assertThat(events.getFirst()).doesNotContainKey("getNullValue");
    }

    private static void fireOneTestCaptureEvent(
        AgentEventCapture eventCapture, PluginManager pluginManager, JavaPlugin plugin)
        throws ClassNotFoundException, EventException
    {
        final ArgumentCaptor<EventExecutor> executorCaptor = ArgumentCaptor.forClass(EventExecutor.class);
        eventCapture.registerListener(TestCaptureEvent.class.getName());
        verify(pluginManager).registerEvent(
            eq(TestCaptureEvent.class),
            any(Listener.class),
            eq(EventPriority.MONITOR),
            executorCaptor.capture(),
            eq(plugin),
            eq(false)
        );
        executorCaptor.getValue().execute(mock(Listener.class), new TestCaptureEvent("value", false));
    }

    public static final class TestCaptureEvent extends Event
    {
        private static final HandlerList HANDLERS = new HandlerList();
        private final String value;
        private final boolean cancelled;

        private TestCaptureEvent(String value, boolean cancelled)
        {
            this.value = value;
            this.cancelled = cancelled;
        }

        public String getValue()
        {
            return value;
        }

        public boolean isCancelled()
        {
            return cancelled;
        }

        @Override
        public HandlerList getHandlers()
        {
            return HANDLERS;
        }

        @SuppressWarnings("unused")
        public static HandlerList getHandlerList()
        {
            return HANDLERS;
        }
    }

    public static final class MixedFieldsEvent extends Event
    {
        private static final HandlerList HANDLERS = new HandlerList();
        private final String label;
        private final @org.jspecify.annotations.Nullable Object nullValue;

        private MixedFieldsEvent(String label, @org.jspecify.annotations.Nullable Object nullValue)
        {
            this.label = label;
            this.nullValue = nullValue;
        }

        public String getLabel()
        {
            return label;
        }

        @SuppressWarnings("unused")
        public @org.jspecify.annotations.Nullable Object getNullValue()
        {
            return nullValue;
        }

        @Override
        public HandlerList getHandlers()
        {
            return HANDLERS;
        }

        @SuppressWarnings("unused")
        public static HandlerList getHandlerList()
        {
            return HANDLERS;
        }
    }

    public static final class ThrowingGetterEvent extends Event
    {
        private static final HandlerList HANDLERS = new HandlerList();

        private ThrowingGetterEvent()
        {
        }

        @SuppressWarnings("unused")
        public String getBroken()
        {
            throw new IllegalStateException("boom");
        }

        @Override
        public HandlerList getHandlers()
        {
            return HANDLERS;
        }

        @SuppressWarnings("unused")
        public static HandlerList getHandlerList()
        {
            return HANDLERS;
        }
    }
}
