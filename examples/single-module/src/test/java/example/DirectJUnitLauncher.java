package example;

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;
import java.util.Arrays;

/** Standalone JUnit launcher used by the executable consumer regression checks. */
public final class DirectJUnitLauncher
{
    /** Executes class or fully qualified method selectors and fails on missing or unsuccessful tests. */
    public static void main(String[] args)
    {
        final DiscoverySelector[] selectors = args.length == 0
            ? new DiscoverySelector[]{DiscoverySelectors.selectClass("example.SourceFreshnessIT")}
            : Arrays.stream(args)
                .map(value -> value.contains("#")
                    ? DiscoverySelectors.selectMethod(value)
                    : DiscoverySelectors.selectClass(value))
                .toArray(DiscoverySelector[]::new);
        final var request = LauncherDiscoveryRequestBuilder.request().selectors(selectors).build();
        final var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        final var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        summary.printFailuresTo(new PrintWriter(System.out, true));
        if (summary.getTestsFoundCount() != Integer.getInteger("expected.test.count", 1) ||
            summary.getTotalFailureCount() != 0)
        {
            throw new AssertionError("Direct JUnit launch failed");
        }
    }
}
