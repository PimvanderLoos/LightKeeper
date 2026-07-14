package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Writes per-test diagnostics bundles for LightKeeper-managed tests.
 *
 * <p>A bundle is a directory of plain-text files under the configured reports root:
 * {@code <root>/<TestClass>/<testMethod>-<timestamp>/} containing the test outcome and failure
 * ({@code outcome.txt}), all captured server errors ({@code server-errors.txt}), and the captured server
 * console output ({@code server-output.log}).
 *
 * <p>Writing is strictly best-effort: diagnostics must never fail (or further fail) a test run. Every data
 * source is captured independently, so one broken source (e.g. a dead agent connection) still leaves the other
 * files intact, with the failure recorded in place of the missing content.
 */
public final class FailureDiagnosticsWriter
{
    private static final System.Logger LOG = System.getLogger(FailureDiagnosticsWriter.class.getName());

    private FailureDiagnosticsWriter()
    {
    }

    /**
     * Writes a diagnostics bundle for a single test.
     *
     * @param framework
     *     The framework whose state to capture; must still be open.
     * @param reportsRoot
     *     Root directory for diagnostics bundles; created when missing.
     * @param testClassName
     *     Simple name of the test class, used as the bundle's parent directory.
     * @param testMethodName
     *     Name of the test method, used in the bundle directory's name.
     * @param failure
     *     The test's execution failure, or {@code null} when the test passed (e.g. in {@code always} mode).
     * @return The bundle directory, or {@code null} when nothing could be written.
     */
    public static @Nullable Path write(
        ILightkeeperFramework framework,
        Path reportsRoot,
        String testClassName,
        String testMethodName,
        @Nullable Throwable failure)
    {
        try
        {
            final String timestamp = Instant.now().toString().replace(':', '-');
            final Path bundleDirectory = reportsRoot
                .resolve(sanitizeFileName(testClassName))
                .resolve(sanitizeFileName(testMethodName) + "-" + timestamp);
            Files.createDirectories(bundleDirectory);

            writeOutcome(bundleDirectory, testClassName, testMethodName, failure);
            writeSection(bundleDirectory, "server-errors.txt", () -> renderServerErrors(framework));
            writeSection(bundleDirectory, "server-output.log", () -> renderServerOutput(framework));

            LOG.log(
                System.Logger.Level.WARNING,
                () -> "LK_DIAGNOSTICS: Wrote diagnostics bundle for %s.%s to '%s'."
                    .formatted(testClassName, testMethodName, bundleDirectory)
            );
            return bundleDirectory;
        }
        catch (Exception exception)
        {
            LOG.log(
                System.Logger.Level.WARNING,
                "LK_DIAGNOSTICS: Failed to write a diagnostics bundle for %s.%s."
                    .formatted(testClassName, testMethodName),
                exception
            );
            return null;
        }
    }

    private static void writeOutcome(
        Path bundleDirectory,
        String testClassName,
        String testMethodName,
        @Nullable Throwable failure)
        throws IOException
    {
        final StringBuilder outcome = new StringBuilder(256)
            .append("test: ").append(testClassName).append('.').append(testMethodName)
            .append(System.lineSeparator())
            .append("outcome: ").append(failure == null ? "PASSED" : "FAILED")
            .append(System.lineSeparator());
        if (failure != null)
        {
            final StringWriter stackTrace = new StringWriter();
            failure.printStackTrace(new PrintWriter(stackTrace));
            outcome.append(System.lineSeparator()).append(stackTrace);
        }
        Files.writeString(bundleDirectory.resolve("outcome.txt"), outcome.toString());
    }

    /**
     * Captures one data source into a file, recording the capture failure in the file itself when the source
     * throws — one broken source must not take down the rest of the bundle.
     */
    private static void writeSection(Path bundleDirectory, String fileName, ContentSupplier contentSupplier)
        throws IOException
    {
        String content;
        try
        {
            content = contentSupplier.get();
        }
        catch (Exception exception)
        {
            final StringWriter stackTrace = new StringWriter();
            exception.printStackTrace(new PrintWriter(stackTrace));
            content = "Failed to capture this section: " + stackTrace;
        }
        Files.writeString(bundleDirectory.resolve(fileName), content);
    }

    private static String renderServerErrors(ILightkeeperFramework framework)
    {
        final List<ServerErrorSnapshot> capturedErrors = framework.serverErrors().getCaptured();
        if (capturedErrors.isEmpty())
            return "No captured server errors." + System.lineSeparator();

        final StringBuilder rendered = new StringBuilder(1024);
        for (final ServerErrorSnapshot error : capturedErrors)
            rendered.append(error.toDisplayString(Integer.MAX_VALUE));
        return rendered.toString();
    }

    private static String renderServerOutput(ILightkeeperFramework framework)
    {
        return String.join(System.lineSeparator(), framework.serverOutput()) + System.lineSeparator();
    }

    /**
     * Replaces every character that is not safe in a file name across platforms with an underscore.
     */
    private static String sanitizeFileName(String name)
    {
        final StringBuilder sanitized = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++)
        {
            final char character = name.charAt(i);
            final boolean safe = (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '.' || character == '-' || character == '_';
            sanitized.append(safe ? character : '_');
        }
        final String result = sanitized.toString();
        return result.isEmpty() ? "unnamed" : result;
    }

    @FunctionalInterface
    private interface ContentSupplier
    {
        String get();
    }
}
