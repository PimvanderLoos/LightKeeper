package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

/**
 * Exercises the diagnostics-on-failure bundle writer end-to-end.
 *
 * <p>The bundle is written by the extension's {@code afterEach}, so no test can observe its own bundle; the
 * first (ordered) test enables {@code always} mode into a dedicated directory, and the second test asserts the
 * first test's bundle content before restoring the previous configuration. An {@link AfterAll} hook restores
 * the configuration even when the flow fails halfway, so {@code always} mode cannot leak into other IT classes
 * in the same JVM.
 */
@ExtendWith(LightkeeperExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LightkeeperDiagnosticsIT
{
    private static final Path REPORTS_ROOT = Path.of("target", "lightkeeper-reports-it");

    private static @Nullable String previousMode;
    private static @Nullable String previousDirectory;
    private static boolean propertiesModified;

    @Test
    @Order(1)
    void diagnostics_shouldEnableAlwaysModeForThisTest(ILightkeeperFramework framework)
    {
        // setup
        previousMode = System.getProperty("lightkeeper.diagnostics");
        previousDirectory = System.getProperty("lightkeeper.diagnosticsDirectory");
        propertiesModified = true;
        System.setProperty("lightkeeper.diagnostics", "always");
        System.setProperty("lightkeeper.diagnosticsDirectory", REPORTS_ROOT.toString());

        // execute + verify: a trivially passing interaction; the bundle itself is written in afterEach
        assertThat(framework.mainWorld()).hasNonBlankName();
    }

    @Test
    @Order(2)
    void diagnostics_shouldHaveWrittenBundleForPreviousTest(ILightkeeperFramework framework)
        throws IOException
    {
        // setup: restore the previous configuration first so THIS test's afterEach writes nothing
        restoreDiagnosticsProperties();
        final Path classDirectory = REPORTS_ROOT.resolve(getClass().getSimpleName());

        // execute: pick the newest matching bundle (ISO timestamps sort lexicographically), so stale bundles
        // from earlier non-clean runs cannot be selected.
        final Path bundleDirectory;
        try (Stream<Path> bundles = Files.list(classDirectory))
        {
            bundleDirectory = bundles
                .filter(path -> path.getFileName().toString().startsWith("diagnostics_shouldEnableAlwaysMode"))
                .max(Comparator.comparing(path -> path.getFileName().toString()))
                .orElseThrow(() -> new AssertionError(
                    "No diagnostics bundle found under " + classDirectory));
        }

        // verify
        assertThat(Files.readString(bundleDirectory.resolve("outcome.txt")))
            .contains("outcome: PASSED")
            .contains("diagnostics_shouldEnableAlwaysModeForThisTest");
        assertThat(bundleDirectory.resolve("server-errors.txt")).exists();
        assertThat(Files.readString(bundleDirectory.resolve("server-output.log")))
            .contains("Done");
    }

    @AfterAll
    static void restoreDiagnosticsConfiguration()
    {
        restoreDiagnosticsProperties();
    }

    private static void restoreDiagnosticsProperties()
    {
        if (!propertiesModified)
            return;
        restoreProperty("lightkeeper.diagnostics", previousMode);
        restoreProperty("lightkeeper.diagnosticsDirectory", previousDirectory);
    }

    private static void restoreProperty(String key, @Nullable String previousValue)
    {
        if (previousValue == null)
            System.clearProperty(key);
        else
            System.setProperty(key, previousValue);
    }
}
