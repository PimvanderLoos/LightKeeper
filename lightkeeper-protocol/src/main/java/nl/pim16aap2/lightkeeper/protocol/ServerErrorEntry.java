package nl.pim16aap2.lightkeeper.protocol;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Typed snapshot of a single captured server error or warning, carried directly inside protocol responses.
 *
 * <p>Entries are captured by the agent's Log4j appender as structured log events, so the throwable metadata
 * reflects the actual {@code Throwable} attached to the log event rather than text scraped from console output.
 *
 * @param timestampMillis
 *     Epoch milliseconds at which the log event was created.
 * @param severity
 *     Severity bucket derived from the numeric log level: {@code "ERROR"} for error-or-worse events,
 *     {@code "WARNING"} otherwise.
 * @param levelName
 *     Original log level name (e.g. {@code "ERROR"}, {@code "WARN"}, {@code "FATAL"}) for display purposes.
 * @param loggerName
 *     Name of the logger that emitted the event.
 * @param threadName
 *     Name of the thread that emitted the event.
 * @param message
 *     Formatted log message.
 * @param throwableClass
 *     Fully-qualified class name of the attached throwable, or {@code null} when the event carried none.
 * @param throwableMessage
 *     Message of the attached throwable, or {@code null} when absent.
 * @param stackTrace
 *     Rendered stack trace lines of the attached throwable including its cause chain (possibly truncated);
 *     empty when the event carried no throwable.
 */
public record ServerErrorEntry(
    long timestampMillis,
    String severity,
    String levelName,
    String loggerName,
    String threadName,
    String message,
    @Nullable String throwableClass,
    @Nullable String throwableMessage,
    List<String> stackTrace
)
{
    /**
     * Validates required fields and defensively copies the stack trace.
     */
    public ServerErrorEntry
    {
        ProtocolPreconditions.requireNonBlank(severity, "severity");
        ProtocolPreconditions.requireNonBlank(levelName, "levelName");
        ProtocolPreconditions.requireNonNull(loggerName, "loggerName");
        ProtocolPreconditions.requireNonNull(threadName, "threadName");
        ProtocolPreconditions.requireNonNull(message, "message");
        stackTrace = stackTrace == null ? List.of() : List.copyOf(stackTrace);
    }
}
