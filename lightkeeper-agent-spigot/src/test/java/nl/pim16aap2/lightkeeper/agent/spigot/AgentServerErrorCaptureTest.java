package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.ServerErrorEntry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

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
}
