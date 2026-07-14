package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.ServerErrorEntry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.status.StatusData;
import org.apache.logging.log4j.status.StatusListener;
import org.apache.logging.log4j.status.StatusLogger;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Captures WARN-or-worse server log events as structured snapshots by attaching an appender to the Log4j root
 * logger.
 *
 * <p>Unlike console scraping, capture happens at the {@link LogEvent} layer where the original {@link Throwable}
 * is still attached, so entries carry the real exception class, message, and rendered cause chain. Because the
 * server redirects {@code System.out}/{@code System.err} into Log4j during startup, raw
 * {@code printStackTrace()} calls from plugins are captured as well. A {@link StatusListener} is registered
 * alongside the appender so failures inside the logging system itself (which Log4j reports only through its
 * status logger, bypassing all appenders) are captured too.
 *
 * <p>The buffer is bounded: once full, new entries are dropped and counted while the oldest entries are kept,
 * so an error storm cannot exhaust server memory and the first (root-cause) errors always survive.
 *
 * <p>Install from {@code onLoad} — before any plugin's {@code onEnable} runs — so enable-time errors of the
 * plugins under test are captured.
 */
final class AgentServerErrorCapture
{
    /**
     * Maximum number of error entries retained. Prevents unbounded memory growth during error storms.
     */
    static final int MAX_CAPTURED_ERRORS = 1_000;
    /**
     * Maximum number of rendered stack trace lines (including the cause chain) retained per entry.
     */
    static final int MAX_STACK_TRACE_LINES = 100;
    /**
     * Severity bucket for error-or-worse events.
     */
    static final String SEVERITY_ERROR = "ERROR";
    /**
     * Severity bucket for warning events.
     */
    static final String SEVERITY_WARNING = "WARNING";
    /**
     * Name under which the capture appender is registered on the root logger.
     */
    private static final String APPENDER_NAME = "LightKeeperServerErrorCapture";
    /**
     * Synthetic logger name used for entries captured from Log4j's internal status logger.
     */
    private static final String STATUS_LOGGER_NAME = "log4j.StatusLogger";

    /**
     * Plugin logger used for install/uninstall diagnostics only; never used from the capture path, which must
     * not log (logging from inside the appender would recurse straight back into it).
     */
    private final java.util.logging.Logger pluginLogger;
    /**
     * Guards {@link #buffer} and {@link #droppedCount}.
     */
    private final Object bufferLock = new Object();
    /**
     * Captured entries ordered oldest-to-newest; guarded by {@link #bufferLock}.
     */
    private final ArrayDeque<ServerErrorEntry> buffer = new ArrayDeque<>();
    /**
     * Number of entries discarded because the buffer was full; guarded by {@link #bufferLock}.
     */
    private long droppedCount;

    /**
     * The capture appender while installed.
     */
    private @Nullable CaptureAppender appender;
    /**
     * The status listener while installed.
     */
    private @Nullable CaptureStatusListener statusListener;
    /**
     * Whether the appender is currently attached to the root logger.
     */
    private volatile boolean captureActive;

    /**
     * @param pluginLogger
     *     Logger used to report install/uninstall problems.
     */
    AgentServerErrorCapture(java.util.logging.Logger pluginLogger)
    {
        this.pluginLogger = Objects.requireNonNull(pluginLogger, "pluginLogger");
    }

    /**
     * Attaches the capture appender to the Log4j root logger and registers the status listener.
     *
     * <p>Failures are reported through the plugin logger and leave capture inactive rather than breaking agent
     * startup; {@link #active()} reflects the outcome.
     *
     * @return
     *     {@code true} when capture is active after this call.
     */
    boolean install()
    {
        if (captureActive)
            return true;

        try
        {
            if (!(LogManager.getRootLogger() instanceof org.apache.logging.log4j.core.Logger coreLogger))
            {
                pluginLogger.warning(
                    "Log4j root logger is not a log4j-core logger; structured server-error capture is disabled.");
                return false;
            }

            final CaptureAppender captureAppender = new CaptureAppender();
            captureAppender.start();
            try
            {
                coreLogger.addAppender(captureAppender);
            }
            catch (RuntimeException | LinkageError exception)
            {
                // Never leak a started appender that was not attached.
                captureAppender.stop();
                throw exception;
            }

            final CaptureStatusListener capturedStatusListener;
            try
            {
                capturedStatusListener = new CaptureStatusListener();
                StatusLogger.getLogger().registerListener(capturedStatusListener);
            }
            catch (RuntimeException | LinkageError exception)
            {
                // Roll back the already-attached appender: without the field set, uninstall() could
                // never remove it, leaving a half-installed capture on the root logger.
                coreLogger.removeAppender(captureAppender);
                captureAppender.stop();
                throw exception;
            }

            appender = captureAppender;
            statusListener = capturedStatusListener;
            captureActive = true;
            return true;
        }
        catch (RuntimeException | LinkageError exception)
        {
            pluginLogger.warning(
                "Failed to attach the structured server-error capture appender: " + exception);
            return false;
        }
    }

    /**
     * Detaches the capture appender and status listener; safe to call when capture never became active.
     *
     * <p>The root logger is a singleton, so re-resolving it here detaches from the same logger the appender was
     * added to during {@link #install()}.
     */
    void uninstall()
    {
        final CaptureAppender captureAppender = appender;
        final CaptureStatusListener capturedStatusListener = statusListener;
        appender = null;
        statusListener = null;
        captureActive = false;

        try
        {
            if (captureAppender != null
                && LogManager.getRootLogger() instanceof org.apache.logging.log4j.core.Logger coreLogger)
            {
                coreLogger.removeAppender(captureAppender);
                captureAppender.stop();
            }
            if (capturedStatusListener != null)
                StatusLogger.getLogger().removeListener(capturedStatusListener);
        }
        catch (RuntimeException | LinkageError exception)
        {
            pluginLogger.warning("Failed to detach the structured server-error capture appender: " + exception);
        }
    }

    /**
     * Reports whether the capture appender is currently attached.
     *
     * @return
     *     {@code true} while capture is active.
     */
    boolean active()
    {
        return captureActive;
    }

    /**
     * Returns a snapshot of captured entries ordered oldest-to-newest.
     *
     * @return
     *     Snapshot list of captured entries.
     */
    List<ServerErrorEntry> snapshot()
    {
        synchronized (bufferLock)
        {
            return new ArrayList<>(buffer);
        }
    }

    /**
     * Returns the number of entries dropped because the buffer was full.
     *
     * @return
     *     Dropped entry count since agent load or the last {@link #clear()}.
     */
    long droppedCount()
    {
        synchronized (bufferLock)
        {
            return droppedCount;
        }
    }

    /**
     * Discards all captured entries and resets the dropped-entry counter.
     */
    void clear()
    {
        synchronized (bufferLock)
        {
            buffer.clear();
            droppedCount = 0L;
        }
    }

    /**
     * Buffers one entry, dropping it (counted) when the buffer is full so the oldest entries survive.
     *
     * @param entry
     *     The entry to buffer.
     */
    private void offer(ServerErrorEntry entry)
    {
        synchronized (bufferLock)
        {
            if (buffer.size() >= MAX_CAPTURED_ERRORS)
            {
                droppedCount++;
                return;
            }
            buffer.addLast(entry);
        }
    }

    /**
     * Maps a WARN-or-worse log event into a structured entry and buffers it; sub-WARN events are ignored.
     *
     * @param event
     *     The log event to capture.
     */
    private void capture(LogEvent event)
    {
        final Level level = event.getLevel();
        if (level == null || !level.isMoreSpecificThan(Level.WARN))
            return;

        final Throwable thrown = event.getThrown();
        offer(new ServerErrorEntry(
            event.getTimeMillis(),
            level.isMoreSpecificThan(Level.ERROR) ? SEVERITY_ERROR : SEVERITY_WARNING,
            level.name(),
            Objects.requireNonNullElse(event.getLoggerName(), ""),
            Objects.requireNonNullElse(event.getThreadName(), ""),
            event.getMessage() == null ? "" : Objects.requireNonNullElse(
                event.getMessage().getFormattedMessage(), ""),
            thrown == null ? null : thrown.getClass().getName(),
            thrown == null ? null : thrown.getMessage(),
            thrown == null ? List.of() : renderStackTrace(thrown)
        ));
    }

    /**
     * Renders a throwable's full stack trace (including cause chain) as lines, truncated to
     * {@link #MAX_STACK_TRACE_LINES}.
     *
     * @param thrown
     *     The throwable to render.
     * @return
     *     Rendered stack trace lines.
     */
    private static List<String> renderStackTrace(Throwable thrown)
    {
        final StringWriter stringWriter = new StringWriter();
        thrown.printStackTrace(new PrintWriter(stringWriter));
        final List<String> lines = stringWriter.toString().lines().toList();
        if (lines.size() <= MAX_STACK_TRACE_LINES)
            return lines;

        final List<String> truncated = new ArrayList<>(lines.subList(0, MAX_STACK_TRACE_LINES));
        truncated.add("... (%d more lines truncated)".formatted(lines.size() - MAX_STACK_TRACE_LINES));
        return truncated;
    }

    /**
     * Appender that funnels WARN-or-worse root-logger events into the capture buffer.
     */
    private final class CaptureAppender extends AbstractAppender
    {
        CaptureAppender()
        {
            super(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event)
        {
            try
            {
                capture(event);
            }
            catch (RuntimeException exception)
            {
                // Never propagate capture failures into the server's logging pipeline, and never log from here:
                // a log call would recurse straight back into this appender. Count the loss instead.
                synchronized (bufferLock)
                {
                    droppedCount++;
                }
            }
        }
    }

    /**
     * Status listener that captures Log4j-internal failures, which are reported only through the status logger
     * and would otherwise bypass every appender (they are written to the raw stderr file descriptor).
     */
    private final class CaptureStatusListener implements StatusListener
    {
        @Override
        public void log(StatusData data)
        {
            try
            {
                final Level level = data.getLevel();
                if (level == null || !level.isMoreSpecificThan(Level.WARN))
                    return;

                final Throwable thrown = data.getThrowable();
                offer(new ServerErrorEntry(
                    data.getTimestamp(),
                    level.isMoreSpecificThan(Level.ERROR) ? SEVERITY_ERROR : SEVERITY_WARNING,
                    level.name(),
                    STATUS_LOGGER_NAME,
                    Objects.requireNonNullElse(data.getThreadName(), ""),
                    Objects.requireNonNullElse(data.getFormattedStatus(), ""),
                    thrown == null ? null : thrown.getClass().getName(),
                    thrown == null ? null : thrown.getMessage(),
                    thrown == null ? List.of() : renderStackTrace(thrown)
                ));
            }
            catch (RuntimeException exception)
            {
                // Same rule as the appender: never log from the capture path.
                synchronized (bufferLock)
                {
                    droppedCount++;
                }
            }
        }

        @Override
        public Level getStatusLevel()
        {
            return Level.WARN;
        }

        @Override
        public void close()
        {
            // Nothing to release; unregistration happens in uninstall().
        }
    }
}
