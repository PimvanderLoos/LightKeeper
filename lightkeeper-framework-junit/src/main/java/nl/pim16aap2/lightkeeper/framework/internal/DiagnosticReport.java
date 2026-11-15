package nl.pim16aap2.lightkeeper.framework.internal;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** One self-contained, escaped overview for build, server-lifecycle and per-test diagnostic bundles. */
final class DiagnosticReport
{
    /** Raw files stay intact; only bounded, redacted excerpts are copied into the overview. */
    private static final int EXCERPT_BYTES = 64 * 1024;
    /** Explicit allowlist excludes runtime manifests and arbitrary files in a consumer directory. */
    private static final List<String> FILES = List.of(
        "outcome.txt", "failure.txt", "server-errors.txt", "server-output.log", "maven.log"
    );
    /** Report creation is best-effort and must never replace the original failure. */
    private static final System.Logger LOG = System.getLogger(DiagnosticReport.class.getName());

    private DiagnosticReport()
    {
    }

    static @Nullable Path write(
        Path directory,
        String phase,
        String context,
        @Nullable Throwable failure,
        String action
    )
    {
        try
        {
            final StringBuilder report = new StringBuilder("""
                <!doctype html><html lang="en"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>LightKeeper diagnostics</title>
                <style>body{max-width:80rem;margin:2rem auto;padding:0 1rem;font:1rem/1.5 system-ui}
                pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#eee;padding:1rem}</style>
                <h1>LightKeeper diagnostics</h1>
                """);
            report.append("<h2>Phase</h2><p>").append(DiagnosticText.html(phase))
                .append("</p><h2>Location</h2><pre>").append(DiagnosticText.html(context))
                .append("</pre><h2>Result</h2><pre>").append(DiagnosticText.html(describe(failure)))
                .append("</pre><h2>Next action</h2><p>").append(DiagnosticText.html(action)).append("</p>");
            for (final String name : FILES)
            {
                final Path file = directory.resolve(name);
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                {
                    report.append("<h2><a href=\"").append(name).append("\">").append(name)
                        .append("</a></h2><p>Last 64 KiB excerpt; open the file for the original log.</p><pre>")
                        .append(DiagnosticText.html(tail(file))).append("</pre>");
                }
            }
            final Path destination = directory.resolve("index.html");
            Files.writeString(destination, report.toString(), StandardCharsets.UTF_8);
            return destination;
        }
        catch (IOException | RuntimeException exception)
        {
            LOG.log(System.Logger.Level.WARNING, "LK_DIAGNOSTICS: Could not write the overview; raw logs remain.",
                exception);
            return null;
        }
    }

    private static String describe(@Nullable Throwable failure)
    {
        if (failure == null)
        {
            return "PASSED — captured because diagnostics mode requests all outcomes.";
        }
        final StringBuilder detail = new StringBuilder();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; ++depth)
        {
            detail.append(current.getClass().getSimpleName()).append(": ").append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return detail.toString();
    }

    private static String tail(Path file)
        throws IOException
    {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))
        {
            channel.position(Math.max(0, channel.size() - EXCERPT_BYTES));
            final ByteBuffer bytes = ByteBuffer.allocate(EXCERPT_BYTES);
            while (bytes.hasRemaining() && channel.read(bytes) > 0)
            {
                // Bounded even if another process is still appending output.
            }
            bytes.flip();
            return StandardCharsets.UTF_8.decode(bytes).toString();
        }
    }
}
