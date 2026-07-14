package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

/**
 * Exercises the diagnostics-on-failure bundle writer end-to-end.
 *
 * <p>The bundle is written by the extension's {@code afterEach}, so no test can observe its own bundle; the
 * first (ordered) test enables {@code always} mode into a dedicated directory, and the second test asserts the
 * first test's bundle content before restoring the default mode.
 */
@ExtendWith(LightkeeperExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LightkeeperDiagnosticsIT
{
    private static final Path REPORTS_ROOT = Path.of("target", "lightkeeper-reports-it");

    @Test
    @Order(1)
    void diagnostics_shouldEnableAlwaysModeForThisTest(ILightkeeperFramework framework)
    {
        // setup
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
        // setup: restore the default mode first so THIS test's afterEach writes nothing
        System.clearProperty("lightkeeper.diagnostics");
        System.clearProperty("lightkeeper.diagnosticsDirectory");
        final Path classDirectory = REPORTS_ROOT.resolve(getClass().getSimpleName());

        // execute
        final Path bundleDirectory;
        try (Stream<Path> bundles = Files.list(classDirectory))
        {
            bundleDirectory = bundles
                .filter(path -> path.getFileName().toString().startsWith("diagnostics_shouldEnableAlwaysMode"))
                .findFirst()
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
}
