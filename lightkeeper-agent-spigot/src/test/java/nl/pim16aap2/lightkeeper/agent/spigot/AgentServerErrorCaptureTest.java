package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.ServerErrorEntry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.status.StatusData;
import org.apache.logging.log4j.status.StatusListener;
import org.apache.logging.log4j.status.StatusLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link AgentServerErrorCapture} against the real Log4j runtime available on the test classpath:
 * {@code install()} attaches to the actual root logger, so logging through Log4j verifies the full capture path.
 */
class AgentServerErrorCaptureTest
{
    private AgentServerErrorCapture capture;

    @BeforeEach
    void setUp()
    {
        capture = new AgentServerErrorCapture(Logger.getLogger(AgentServerErrorCaptureTest.class.getName()));
    }

    @AfterEach
    void tearDown()
    {
        capture.uninstall();
    }

    @Test
    void install_shouldActivateCaptureAndBeIdempotent()
    {
        // execute
        final boolean firstInstall = capture.install();
        final boolean secondInstall = capture.install();

        // verify
        assertThat(firstInstall).isTrue();
        assertThat(secondInstall).isTrue();
        assertThat(capture.active()).isTrue();
    }

    @Test
    void uninstall_shouldDeactivateCaptureAndBeSafeWithoutInstall()
    {
        // setup
        capture.install();

        // execute
        capture.uninstall();
        capture.uninstall();

        // verify
        assertThat(capture.active()).isFalse();
    }

    @Test
    void snapshot_shouldContainErrorLoggedThroughLog4jWithThrowableMetadata()
    {
        // setup
        capture.install();
        final IllegalStateException thrown = new IllegalStateException("capture-test-boom");

        // execute
        LogManager.getLogger("lightkeeper.capture.test").error("capture-test-message", thrown);

        // verify
        final List<ServerErrorEntry> entries = capture.snapshot().stream()
            .filter(entry -> "capture-test-message".equals(entry.message()))
            .toList();
        assertThat(entries).hasSize(1);
        final ServerErrorEntry entry = entries.getFirst();
        assertThat(entry.severity()).isEqualTo(AgentServerErrorCapture.SEVERITY_ERROR);
        assertThat(entry.levelName()).isEqualTo("ERROR");
        assertThat(entry.loggerName()).isEqualTo("lightkeeper.capture.test");
        assertThat(entry.threadName()).isNotBlank();
        assertThat(entry.timestampMillis()).isPositive();
        assertThat(entry.throwableClass()).isEqualTo(IllegalStateException.class.getName());
        assertThat(entry.throwableMessage()).isEqualTo("capture-test-boom");
        assertThat(entry.stackTrace())
            .isNotEmpty()
            .first().asString().contains("capture-test-boom");
    }

    @Test
    void snapshot_shouldBucketWarnAsWarningAndIgnoreInfo()
    {
        // setup
        capture.install();

        // execute
        LogManager.getLogger("lightkeeper.capture.test").warn("capture-test-warning");
        LogManager.getLogger("lightkeeper.capture.test").info("capture-test-info");

        // verify
        final List<ServerErrorEntry> entries = capture.snapshot();
        assertThat(entries)
            .anySatisfy(entry ->
            {
                assertThat(entry.message()).isEqualTo("capture-test-warning");
                assertThat(entry.severity()).isEqualTo(AgentServerErrorCapture.SEVERITY_WARNING);
                assertThat(entry.throwableClass()).isNull();
                assertThat(entry.stackTrace()).isEmpty();
            })
            .noneSatisfy(entry -> assertThat(entry.message()).isEqualTo("capture-test-info"));
    }

    @Test
    void snapshot_shouldRenderCauseChainInStackTrace()
    {
        // setup
        capture.install();
        final RuntimeException thrown =
            new RuntimeException("outer", new IllegalArgumentException("inner-cause"));

        // execute
        LogManager.getLogger("lightkeeper.capture.test").error("capture-test-chained", thrown);

        // verify
        final ServerErrorEntry entry = capture.snapshot().stream()
            .filter(candidate -> "capture-test-chained".equals(candidate.message()))
            .findFirst()
            .orElseThrow();
        assertThat(entry.stackTrace())
            .anySatisfy(line -> assertThat(line).contains("Caused by:").contains("inner-cause"));
    }

    @Test
    void clear_shouldDiscardEntriesAndResetDroppedCount()
    {
        // setup
        capture.install();
        LogManager.getLogger("lightkeeper.capture.test").error("capture-test-to-clear");

        // execute
        capture.clear();

        // verify
        assertThat(capture.snapshot())
            .noneSatisfy(entry -> assertThat(entry.message()).isEqualTo("capture-test-to-clear"));
        assertThat(capture.droppedCount()).isZero();
    }

    @Test
    void offer_shouldDropNewEntriesAndCountThemWhenBufferIsFull()
    {
        // setup
        capture.install();
        final org.apache.logging.log4j.Logger logger = LogManager.getLogger("lightkeeper.capture.test");
        capture.clear();

        // execute
        for (int i = 0; i < AgentServerErrorCapture.MAX_CAPTURED_ERRORS + 5; i++)
            logger.error("capture-test-storm-" + i);

        // verify — the buffer keeps the oldest (root-cause) entries and counts the dropped tail
        assertThat(capture.snapshot()).hasSize(AgentServerErrorCapture.MAX_CAPTURED_ERRORS);
        assertThat(capture.droppedCount()).isEqualTo(5L);
        assertThat(capture.snapshot().getFirst().message()).isEqualTo("capture-test-storm-0");
    }

    @Test
    void snapshot_shouldTruncateOverlongStackTraces()
    {
        // setup
        capture.install();
        final RuntimeException thrown = new RuntimeException("deep");
        thrown.setStackTrace(java.util.stream.IntStream.range(0, AgentServerErrorCapture.MAX_STACK_TRACE_LINES + 50)
            .mapToObj(i -> new StackTraceElement("Class" + i, "method", "File.java", i))
            .toArray(StackTraceElement[]::new));

        // execute
        LogManager.getLogger("lightkeeper.capture.test").error("capture-test-deep", thrown);

        // verify
        final ServerErrorEntry entry = capture.snapshot().stream()
            .filter(candidate -> "capture-test-deep".equals(candidate.message()))
            .findFirst()
            .orElseThrow();
        assertThat(entry.stackTrace()).hasSize(AgentServerErrorCapture.MAX_STACK_TRACE_LINES + 1);
        assertThat(entry.stackTrace().getLast()).contains("truncated");
    }

    @Test
    void snapshot_shouldNotContainEventsLoggedAfterUninstall()
    {
        // setup
        capture.install();
        capture.uninstall();

        // execute
        LogManager.getLogger("lightkeeper.capture.test").error("capture-test-after-uninstall");

        // verify
        assertThat(capture.snapshot())
            .noneSatisfy(entry -> assertThat(entry.message()).isEqualTo("capture-test-after-uninstall"));
    }

    @Test
    void install_shouldReturnFalseWhenRootLoggerIsNotCoreLogger()
    {
        // setup
        final org.apache.logging.log4j.Logger nonCoreRootLogger = mock(org.apache.logging.log4j.Logger.class);
        try (MockedStatic<LogManager> logManagerMock = mockStatic(LogManager.class))
        {
            logManagerMock.when(LogManager::getRootLogger).thenReturn(nonCoreRootLogger);

            // execute
            final boolean installed = capture.install();

            // verify
            assertThat(installed).isFalse();
            assertThat(capture.active()).isFalse();
        }
    }

    @Test
    void install_shouldRollBackAppenderWhenStatusListenerRegistrationFails()
    {
        // setup + execute
        try (MockedStatic<StatusLogger> statusLoggerMock = mockStatic(StatusLogger.class))
        {
            final StatusLogger fakeStatusLogger = mock(StatusLogger.class);
            statusLoggerMock.when(StatusLogger::getLogger).thenReturn(fakeStatusLogger);
            doThrow(new IllegalStateException("register-boom")).when(fakeStatusLogger).registerListener(any());

            final boolean installed = capture.install();

            // verify
            assertThat(installed).isFalse();
            assertThat(capture.active()).isFalse();
        }

        // and the already-attached appender must be rolled back from the real root logger, not left dangling
        final org.apache.logging.log4j.core.Logger coreLogger =
            (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
        assertThat(coreLogger.getAppenders()).doesNotContainKey("LightKeeperServerErrorCapture");
    }

    @Test
    void append_shouldIgnoreEventsWithNullLevel()
    {
        // setup
        capture.install();
        final Appender appender = capturedAppender();
        final LogEvent eventWithNullLevel = mock(LogEvent.class);
        when(eventWithNullLevel.getLevel()).thenReturn(null);

        // execute
        appender.append(eventWithNullLevel);

        // verify
        assertThat(capture.snapshot()).isEmpty();
    }

    @Test
    void append_shouldTreatNullMessageAsEmptyString()
    {
        // setup
        capture.install();
        final Appender appender = capturedAppender();
        final LogEvent event = mock(LogEvent.class);
        when(event.getLevel()).thenReturn(Level.ERROR);
        when(event.getMessage()).thenReturn(null);

        // execute
        appender.append(event);

        // verify
        assertThat(capture.snapshot()).singleElement()
            .extracting(ServerErrorEntry::message)
            .isEqualTo("");
    }

    @Test
    void append_shouldSwallowExceptionAndCountDroppedWhenCaptureFails()
    {
        // setup
        capture.install();
        final Appender appender = capturedAppender();
        final LogEvent brokenEvent = mock(LogEvent.class);
        when(brokenEvent.getLevel()).thenReturn(Level.ERROR);
        when(brokenEvent.getMessage()).thenThrow(new RuntimeException("capture-boom"));

        // execute
        appender.append(brokenEvent);

        // verify — capture failures are never propagated into the server's logging pipeline
        assertThat(capture.snapshot()).isEmpty();
        assertThat(capture.droppedCount()).isEqualTo(1L);
    }

    @Test
    void statusListener_shouldCaptureWarnAndErrorLog4jInternalStatusEvents()
    {
        // setup
        capture.install();
        final StatusListener listener = registeredStatusListener();
        final IllegalStateException thrown = new IllegalStateException("status-boom");
        final StatusData warnStatus = new StatusData(
            null, Level.WARN, new SimpleMessage("status-test-warning"), null, "status-thread");
        final StatusData errorStatus = new StatusData(
            null, Level.ERROR, new SimpleMessage("status-test-error"), thrown, "status-thread");

        // execute
        listener.log(warnStatus);
        listener.log(errorStatus);

        // verify
        final List<ServerErrorEntry> entries = capture.snapshot();
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).loggerName()).isEqualTo("log4j.StatusLogger");
        assertThat(entries.get(0).severity()).isEqualTo(AgentServerErrorCapture.SEVERITY_WARNING);
        assertThat(entries.get(0).message()).contains("status-test-warning");
        assertThat(entries.get(1).severity()).isEqualTo(AgentServerErrorCapture.SEVERITY_ERROR);
        assertThat(entries.get(1).message()).contains("status-test-error");
        assertThat(entries.get(1).throwableClass()).isEqualTo(IllegalStateException.class.getName());
        assertThat(entries.get(1).throwableMessage()).isEqualTo("status-boom");
        assertThat(entries.get(1).stackTrace()).isNotEmpty();
    }

    @Test
    void statusListener_shouldIgnoreBelowWarnStatusData()
    {
        // setup
        capture.install();
        final StatusListener listener = registeredStatusListener();
        final StatusData infoStatus = new StatusData(
            null, Level.INFO, new SimpleMessage("status-test-info"), null, "status-thread");

        // execute
        listener.log(infoStatus);

        // verify
        assertThat(capture.snapshot()).isEmpty();
    }

    @Test
    void statusListener_shouldIgnoreNullLevelStatusData()
    {
        // setup
        capture.install();
        final StatusListener listener = registeredStatusListener();
        final StatusData nullLevelStatus = mock(StatusData.class);
        when(nullLevelStatus.getLevel()).thenReturn(null);

        // execute
        listener.log(nullLevelStatus);

        // verify
        assertThat(capture.snapshot()).isEmpty();
    }

    @Test
    void statusListener_shouldSwallowExceptionAndCountDroppedWhenCaptureFails()
    {
        // setup
        capture.install();
        final StatusListener listener = registeredStatusListener();
        final StatusData brokenStatus = mock(StatusData.class);
        when(brokenStatus.getLevel()).thenReturn(Level.ERROR);
        when(brokenStatus.getFormattedStatus()).thenThrow(new RuntimeException("status-capture-boom"));

        // execute
        listener.log(brokenStatus);

        // verify
        assertThat(capture.snapshot()).isEmpty();
        assertThat(capture.droppedCount()).isEqualTo(1L);
    }

    private static Appender capturedAppender()
    {
        final org.apache.logging.log4j.core.Logger coreLogger =
            (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
        return Objects.requireNonNull(
            coreLogger.getAppenders().get("LightKeeperServerErrorCapture"), "appender");
    }

    private static StatusListener registeredStatusListener()
    {
        final Iterator<StatusListener> iterator = StatusLogger.getLogger().getListeners().iterator();
        assertThat(iterator.hasNext()).isTrue();
        final StatusListener listener = iterator.next();
        assertThat(iterator.hasNext()).isFalse();
        return listener;
    }
}
