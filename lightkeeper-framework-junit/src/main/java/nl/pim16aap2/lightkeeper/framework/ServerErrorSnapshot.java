package nl.pim16aap2.lightkeeper.framework;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Snapshot of a single captured server error or warning.
 *
 * <p>Entries originate from two complementary sources:
 * <ul>
 *     <li><strong>Structured log events</strong> — captured inside the server by the agent's Log4j appender,
 *     carrying the real throwable class, message, and rendered cause chain of the original log event.</li>
 *     <li><strong>Raw stderr output</strong> — stack traces written to the server process's raw stderr file
 *     descriptor, bypassing the logging system entirely (for example Log4j's own status logger or JVM-native
 *     output). These entries use {@code "STDERR"} as {@link #levelName()} and
 *     {@link #LOGGER_NAME_STDERR} as {@link #loggerName()}.</li>
 * </ul>
 *
 * @param timestampMillis
 *     Epoch milliseconds at which the event was created (structured events) or captured (raw stderr output).
 * @param severity
 *     Severity bucket; only {@link Severity#ERROR} entries fail
 *     {@code LightkeeperAssertions.assertThat(framework).hasNoServerErrors()}.
 * @param levelName
 *     Original log level name (e.g. {@code "ERROR"}, {@code "WARN"}), or {@code "STDERR"} for raw stderr output.
 * @param loggerName
 *     Name of the logger that emitted the event, or {@link #LOGGER_NAME_STDERR} for raw stderr output.
 * @param threadName
 *     Name of the emitting thread; empty when unknown.
 * @param message
 *     Formatted log message; for raw stderr output, the first line of the detected stack trace.
 * @param throwableClass
 *     Fully-qualified class name of the attached or detected throwable, or {@code null} when absent.
 * @param throwableMessage
 *     Message of the attached or detected throwable, or {@code null} when absent.
 * @param stackTrace
 *     Rendered stack trace lines including the cause chain (possibly truncated); empty when no throwable was
 *     attached.
 */
public record ServerErrorSnapshot(
    long timestampMillis,
    Severity severity,
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
     * {@link #loggerName()} used for entries detected in the server process's raw stderr output.
     */
    public static final String LOGGER_NAME_STDERR = "process.stderr";

    /**
     * Severity bucket of a captured entry.
     */
    public enum Severity
    {
        /**
         * Warning-level entry; buffered for diagnostics but does not fail {@code hasNoServerErrors()}.
         */
        WARNING,
        /**
         * Error-or-worse entry; fails {@code hasNoServerErrors()} unless allowlisted.
         */
        ERROR
    }

    /**
     * Validates required fields and defensively copies the stack trace.
     */
    public ServerErrorSnapshot
    {
        Objects.requireNonNull(severity, "severity may not be null.");
        Objects.requireNonNull(levelName, "levelName may not be null.");
        Objects.requireNonNull(loggerName, "loggerName may not be null.");
        Objects.requireNonNull(threadName, "threadName may not be null.");
        Objects.requireNonNull(message, "message may not be null.");
        stackTrace = stackTrace == null ? List.of() : List.copyOf(stackTrace);
    }

    /**
     * Renders this entry as human-readable text: one header line plus indented stack-trace lines, capped at the
     * given limit with a {@code "... (N more stack trace lines)"} tail when truncated.
     *
     * @param maxStackTraceLines
     *     Maximum number of stack-trace lines to render; use {@link Integer#MAX_VALUE} for the full trace.
     * @return The rendered entry, ending with a line separator.
     */
    public String toDisplayString(int maxStackTraceLines)
    {
        if (maxStackTraceLines < 0)
            throw new IllegalArgumentException("maxStackTraceLines must be >= 0.");

        final StringBuilder rendered = new StringBuilder(128);
        rendered
            .append("[%s] %s (thread: %s): %s".formatted(
                levelName,
                loggerName,
                threadName.isEmpty() ? "?" : threadName,
                message))
            .append(System.lineSeparator());

        stackTrace.stream()
            .limit(maxStackTraceLines)
            .forEach(line -> rendered.append("    ").append(line).append(System.lineSeparator()));
        if (stackTrace.size() > maxStackTraceLines)
            rendered
                .append("    ... (%d more stack trace lines)".formatted(stackTrace.size() - maxStackTraceLines))
                .append(System.lineSeparator());
        return rendered.toString();
    }
}
