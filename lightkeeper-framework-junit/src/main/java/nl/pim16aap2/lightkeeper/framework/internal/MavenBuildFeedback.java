package nl.pim16aap2.lightkeeper.framework.internal;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, single-threaded tailing of a native Maven log, retaining its first useful failure and live phases. */
final class MavenBuildFeedback
{
    /** Maven lifecycle headers identify actual work without copying arbitrary console output. */
    private static final Pattern GOAL = Pattern.compile(
        "--- (?:maven-)?(resources|compiler|jar|shade|assembly):[^: ]+:"
            + "(resources|compile|jar|shade|single) .* @ (\\S+) ---"
    );
    /** The first Maven severity token follows optional timestamps, thread names or other logger prefixes. */
    private static final Pattern LEVEL = Pattern.compile("\\[(ERROR|WARNING|WARN|INFO|DEBUG|TRACE)]");
    /** Maximum bytes consumed during one polling interval. */
    private static final int POLL_BYTES = 64 * 1024;
    /** No line can retain unbounded process output. */
    private static final int LINE_BYTES = 8192;
    /** Full original output, owned by the native build. */
    private final Path log;
    /** Progress sink, injected so tests can observe output before the child exits. */
    private final Consumer<String> publish;
    /** Read offset; no channel is held open between polls. */
    private long offset;
    /** Incomplete UTF-8 lines are decoded only after a newline or process exit. */
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    /** Deduplication is bounded by the number of selected build phases/modules. */
    private final Set<String> reported = new HashSet<>();
    /** Last observed actual lifecycle phase. */
    private String phase = "Evaluating Maven reactor and packaging current production inputs";
    /** First actionable error; the complete Maven log is always retained separately. */
    private @Nullable String problem;

    MavenBuildFeedback(Path log, Consumer<String> publish)
    {
        this.log = log;
        this.publish = publish;
    }

    void drain()
    {
        read(POLL_BYTES, false);
    }

    void finish()
    {
        read(4 * POLL_BYTES, true);
        if (pending.size() > 0)
        {
            line();
        }
    }

    String phase()
    {
        return phase;
    }

    @Nullable String problem()
    {
        return problem;
    }

    private void read(int limit, boolean tail)
    {
        if (!Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS))
        {
            return;
        }
        try (FileChannel channel = FileChannel.open(log, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))
        {
            if (channel.size() < offset || (tail && channel.size() - offset > limit))
            {
                offset = Math.max(0, channel.size() - limit);
                pending.reset();
            }
            channel.position(offset);
            final ByteBuffer bytes = ByteBuffer.allocate(limit);
            final int count = channel.read(bytes);
            if (count < 0)
            {
                return;
            }
            offset += count;
            bytes.flip();
            while (bytes.hasRemaining())
            {
                final byte value = bytes.get();
                if (value == '\n')
                {
                    line();
                }
                else if (pending.size() < LINE_BYTES)
                {
                    pending.write(value);
                }
            }
        }
        catch (IOException exception)
        {
            // Feedback is best-effort; missing live output must never change the producer's result.
        }
    }

    private void line()
    {
        final String line = DiagnosticText.redact(pending.toString(StandardCharsets.UTF_8)).trim();
        pending.reset();
        final Matcher goal = GOAL.matcher(line);
        if (goal.find())
        {
            final String operation = switch (goal.group(1))
            {
                case "resources" -> "Preparing production resources";
                case "compiler" -> "Compiling production inputs";
                default -> "Packaging plugin";
            };
            progress(operation + " — " + goal.group(3));
        }
        final int lease = line.indexOf("LK_BUILD_LOCK:");
        if (lease >= 0)
        {
            progress(line.substring(lease + "LK_BUILD_LOCK:".length()).trim());
        }
        final int marker = line.indexOf("LK_PHASE:");
        if (marker >= 0)
        {
            progress(line.substring(marker + "LK_PHASE:".length()).trim());
        }
        final Matcher level = LEVEL.matcher(line);
        if (problem == null && level.find() && "ERROR".equals(level.group(1)))
        {
            final String detail = line.substring(level.end()).trim();
            if (!detail.isEmpty() && !detail.contains("COMPILATION ERROR") && !detail.matches("[- ]+"))
            {
                problem = detail.substring(0, Math.min(detail.length(), 600));
            }
        }
    }

    private void progress(String message)
    {
        phase = message;
        if (reported.size() < 200 && reported.add(message))
        {
            publish.accept("LK_PROGRESS: " + message);
        }
    }
}
