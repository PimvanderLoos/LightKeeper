package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects stack traces in the server process's raw stderr output and groups them into
 * {@link ServerErrorSnapshot}s.
 *
 * <p>The server redirects {@code System.err} into its logging system during startup, so stderr-pipe content is
 * limited to writers that bypass logging entirely (Log4j's status logger, JVM-native output, streams cached
 * before the redirect). Such output is invisible to the agent's structured capture; this scanner is the
 * complementary net. Only exception-shaped blocks become snapshots — benign stderr noise such as JVM warnings
 * is ignored.
 *
 * <p>Grouping is line-based and best-effort: a block starts at a throwable header line (e.g.
 * {@code java.util.concurrent.TimeoutException} or {@code Exception in thread "main" java.lang...}) and extends
 * over its {@code at ...}/{@code Caused by: ...} continuation lines. Continuation lines without a preceding
 * header (a window that begins mid-trace) are ignored.
 */
final class ServerStderrErrorScanner
{
    /**
     * Level name used for snapshots produced by this scanner.
     */
    static final String LEVEL_NAME_STDERR = "STDERR";
    /**
     * Maximum number of continuation lines retained per detected block.
     */
    static final int MAX_BLOCK_LINES = 100;

    /**
     * Matches the header line of a rendered throwable: an optional {@code Exception in thread "..."} prefix,
     * a fully-qualified class name whose simple name ends in {@code Exception}, {@code Error}, or
     * {@code Throwable}, and an optional {@code : message} tail.
     */
    private static final Pattern THROWABLE_HEADER = Pattern.compile(
        "^(?:Exception in thread \"[^\"]*\" )?((?:[\\w$]+\\.)+[\\w$]*(?:Exception|Error|Throwable))(?::\\s?(.*))?$"
    );

    /**
     * Matches continuation lines of a rendered throwable: stack frames, cause/suppressed headers, and frame
     * elision markers.
     */
    private static final Pattern THROWABLE_CONTINUATION = Pattern.compile(
        "^(?:\\s+at\\s.+|Caused by:\\s.+|\\s+Suppressed:\\s.+|\\s*\\.\\.\\. \\d+ more)$"
    );

    private ServerStderrErrorScanner()
    {
    }

    /**
     * Scans captured stderr lines for rendered stack traces.
     *
     * @param stderrLines
     *     Captured stderr lines in arrival order.
     * @return
     *     One snapshot per detected stack-trace block, in arrival order.
     */
    static List<ServerErrorSnapshot> scan(List<MinecraftServerProcess.OutputLine> stderrLines)
    {
        final List<ServerErrorSnapshot> snapshots = new ArrayList<>();
        @Nullable BlockBuilder currentBlock = null;

        for (final MinecraftServerProcess.OutputLine line : stderrLines)
        {
            final Matcher headerMatcher = THROWABLE_HEADER.matcher(line.text());
            if (headerMatcher.matches())
            {
                if (currentBlock != null)
                    snapshots.add(currentBlock.build());
                currentBlock = new BlockBuilder(line, headerMatcher.group(1), headerMatcher.group(2));
                continue;
            }

            if (currentBlock != null && THROWABLE_CONTINUATION.matcher(line.text()).matches())
            {
                currentBlock.addContinuation(line.text());
                continue;
            }

            if (currentBlock != null)
            {
                snapshots.add(currentBlock.build());
                currentBlock = null;
            }
        }

        if (currentBlock != null)
            snapshots.add(currentBlock.build());
        return List.copyOf(snapshots);
    }

    /**
     * Accumulates the lines of one detected stack-trace block.
     */
    private static final class BlockBuilder
    {
        private final MinecraftServerProcess.OutputLine headerLine;
        private final String throwableClass;
        private final @Nullable String throwableMessage;
        private final List<String> blockLines = new ArrayList<>();
        private int truncatedLineCount;

        BlockBuilder(
            MinecraftServerProcess.OutputLine headerLine,
            String throwableClass,
            @Nullable String throwableMessage)
        {
            this.headerLine = headerLine;
            this.throwableClass = throwableClass;
            this.throwableMessage = throwableMessage;
            blockLines.add(headerLine.text());
        }

        void addContinuation(String text)
        {
            if (blockLines.size() >= MAX_BLOCK_LINES)
            {
                truncatedLineCount++;
                return;
            }
            blockLines.add(text);
        }

        ServerErrorSnapshot build()
        {
            final List<String> stackTrace = new ArrayList<>(blockLines);
            if (truncatedLineCount > 0)
                stackTrace.add("... (%d more lines truncated)".formatted(truncatedLineCount));
            return new ServerErrorSnapshot(
                headerLine.timestampMillis(),
                ServerErrorSnapshot.Severity.ERROR,
                LEVEL_NAME_STDERR,
                ServerErrorSnapshot.LOGGER_NAME_STDERR,
                "",
                headerLine.text(),
                throwableClass,
                throwableMessage == null || throwableMessage.isBlank() ? null : throwableMessage,
                stackTrace
            );
        }
    }
}
