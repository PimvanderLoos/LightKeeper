package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerErrorSnapshotTest
{
    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify default-on-null.
    void constructor_shouldDefaultStackTraceToEmptyListWhenNull()
    {
        // setup + execute
        final ServerErrorSnapshot snapshot = new ServerErrorSnapshot(
            1L,
            ServerErrorSnapshot.Severity.ERROR,
            "ERROR",
            "net.example.SomePlugin",
            "Server thread",
            "boom",
            null,
            null,
            null
        );

        // verify
        assertThat(snapshot.stackTrace()).isEmpty();
    }

    @Test
    void toDisplayString_shouldRenderHeaderAndFullStackTrace()
    {
        // setup
        final ServerErrorSnapshot snapshot = snapshot(
            "ERROR", "Server thread", List.of("at a.B.c(B.java:1)", "at d.E.f(E.java:2)"));

        // execute
        final String rendered = snapshot.toDisplayString(Integer.MAX_VALUE);

        // verify
        assertThat(rendered)
            .contains("[ERROR] net.example.SomePlugin (thread: Server thread): boom")
            .contains("    at a.B.c(B.java:1)")
            .contains("    at d.E.f(E.java:2)")
            .doesNotContain("more stack trace lines");
    }

    @Test
    void toDisplayString_shouldCapStackTraceAndReportOmittedLineCount()
    {
        // setup
        final ServerErrorSnapshot snapshot = snapshot(
            "ERROR", "Server thread", List.of("line1", "line2", "line3"));

        // execute
        final String rendered = snapshot.toDisplayString(1);

        // verify
        assertThat(rendered)
            .contains("    line1")
            .doesNotContain("line2")
            .contains("... (2 more stack trace lines)");
    }

    @Test
    void toDisplayString_shouldRenderQuestionMarkWhenThreadNameIsEmpty()
    {
        // setup
        final ServerErrorSnapshot snapshot = snapshot("WARN", "", List.of());

        // execute
        final String rendered = snapshot.toDisplayString(Integer.MAX_VALUE);

        // verify
        assertThat(rendered).contains("(thread: ?)");
    }

    @Test
    void toDisplayString_shouldRejectNegativeMaxStackTraceLines()
    {
        // setup
        final ServerErrorSnapshot snapshot = snapshot("ERROR", "Server thread", List.of());

        // execute + verify
        assertThatThrownBy(() -> snapshot.toDisplayString(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxStackTraceLines");
    }

    private static ServerErrorSnapshot snapshot(String levelName, String threadName, List<String> stackTrace)
    {
        return new ServerErrorSnapshot(
            1L,
            "WARN".equals(levelName) ? ServerErrorSnapshot.Severity.WARNING : ServerErrorSnapshot.Severity.ERROR,
            levelName,
            "net.example.SomePlugin",
            threadName,
            "boom",
            null,
            null,
            stackTrace
        );
    }
}
