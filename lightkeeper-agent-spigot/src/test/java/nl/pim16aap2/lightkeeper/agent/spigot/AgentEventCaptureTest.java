package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
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

class AgentEventCaptureTest
{
    @Test
    void registerListener_shouldCaptureCancelledEventsIntoRegisteredEventList()
        throws Exception
    {
        // setup
        final JavaPlugin plugin = mock();
        final PluginManager pluginManager = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin);
        final ArgumentCaptor<EventExecutor> executorCaptor = ArgumentCaptor.forClass(EventExecutor.class);

        // execute
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
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
    void registerListener_shouldThrowExceptionWhenClassIsNotBukkitEvent()
    {
        // setup
        final AgentEventCapture eventCapture = new AgentEventCapture(mock());

        // execute + verify
        assertThatThrownBy(() -> eventCapture.registerListener(String.class.getName()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a Bukkit Event");
    }

    @Test
    void clearCapturedEvents_shouldRemoveCapturedData()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = registeredCaptureWithOneEvent();

        // execute
        eventCapture.clearCapturedEvents(TestCaptureEvent.class.getName());

        // verify
        assertThat(eventCapture.getCapturedEvents(TestCaptureEvent.class.getName())).isEmpty();
    }

    @Test
    void unregisterListener_shouldRemoveCapturedData()
        throws Exception
    {
        // setup
        final AgentEventCapture eventCapture = registeredCaptureWithOneEvent();

        // execute
        eventCapture.unregisterListener(TestCaptureEvent.class.getName());

        // verify
        assertThat(eventCapture.getCapturedEvents(TestCaptureEvent.class.getName())).isEmpty();
    }

    private static AgentEventCapture registeredCaptureWithOneEvent()
        throws ClassNotFoundException, EventException
    {
        final JavaPlugin plugin = mock();
        final PluginManager pluginManager = mock();
        final AgentEventCapture eventCapture = new AgentEventCapture(plugin);
        final ArgumentCaptor<EventExecutor> executorCaptor = ArgumentCaptor.forClass(EventExecutor.class);
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
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
        executorCaptor.getValue().execute(mock(Listener.class), new TestCaptureEvent("value", false));
        return eventCapture;
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
}
