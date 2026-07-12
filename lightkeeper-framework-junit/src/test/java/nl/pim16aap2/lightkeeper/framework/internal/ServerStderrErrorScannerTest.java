package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import nl.pim16aap2.lightkeeper.framework.internal.MinecraftServerProcess.OutputLine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ServerStderrErrorScannerTest
{
    private static OutputLine stderrLine(String text)
    {
        return new OutputLine(text, true, 1_720_000_000_000L);
    }

    @Test
    void scan_shouldGroupBareThrowableWithFramesIntoOneSnapshot()
    {
        // setup — the exact shape of a raw printStackTrace() that bypassed the logging system
        final List<OutputLine> lines = List.of(
            stderrLine("java.util.concurrent.TimeoutException"),
            stderrLine("\tat java.base/java.util.concurrent.CompletableFuture.timedGet(CompletableFuture.java:1960)"),
            stderrLine("\tat java.base/java.util.concurrent.CompletableFuture.get(CompletableFuture.java:2095)")
        );

        // execute
        final List<ServerErrorSnapshot> snapshots = ServerStderrErrorScanner.scan(lines);

        // verify
        assertThat(snapshots).hasSize(1);
        final ServerErrorSnapshot snapshot = snapshots.getFirst();
        assertThat(snapshot.severity()).isEqualTo(ServerErrorSnapshot.Severity.ERROR);
        assertThat(snapshot.levelName()).isEqualTo("STDERR");
        assertThat(snapshot.loggerName()).isEqualTo(ServerErrorSnapshot.LOGGER_NAME_STDERR);
        assertThat(snapshot.message()).isEqualTo("java.util.concurrent.TimeoutException");
        assertThat(snapshot.throwableClass()).isEqualTo("java.util.concurrent.TimeoutException");
        assertThat(snapshot.throwableMessage()).isNull();
        assertThat(snapshot.stackTrace()).hasSize(3);
        assertThat(snapshot.timestampMillis()).isEqualTo(1_720_000_000_000L);
    }

    @Test
    void scan_shouldExtractMessageAndCauseChain()
    {
        // setup
        final List<OutputLine> lines = List.of(
            stderrLine("java.lang.IllegalStateException: outer failure"),
            stderrLine("\tat net.example.Foo.bar(Foo.java:10)"),
            stderrLine("Caused by: java.lang.IllegalArgumentException: root cause"),
            stderrLine("\tat net.example.Foo.baz(Foo.java:20)"),
            stderrLine("\t... 3 more")
        );

        // execute
        final List<ServerErrorSnapshot> snapshots = ServerStderrErrorScanner.scan(lines);

        // verify
        assertThat(snapshots).hasSize(1);
        final ServerErrorSnapshot snapshot = snapshots.getFirst();
        assertThat(snapshot.throwableClass()).isEqualTo("java.lang.IllegalStateException");
        assertThat(snapshot.throwableMessage()).isEqualTo("outer failure");
        assertThat(snapshot.stackTrace())
            .hasSize(5)
            .contains("Caused by: java.lang.IllegalArgumentException: root cause");
    }

    @Test
    void scan_shouldHandleExceptionInThreadPrefix()
    {
        // setup
        final List<OutputLine> lines = List.of(
            stderrLine("Exception in thread \"Worker-1\" java.lang.RuntimeException: kaboom"),
            stderrLine("\tat net.example.Worker.run(Worker.java:42)")
        );

        // execute
        final List<ServerErrorSnapshot> snapshots = ServerStderrErrorScanner.scan(lines);

        // verify
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().throwableClass()).isEqualTo("java.lang.RuntimeException");
        assertThat(snapshots.getFirst().throwableMessage()).isEqualTo("kaboom");
    }

    @Test
    void scan_shouldSplitConsecutiveTracesIntoSeparateSnapshots()
    {
        // setup
        final List<OutputLine> lines = List.of(
            stderrLine("java.lang.IllegalStateException: first"),
            stderrLine("\tat net.example.Foo.a(Foo.java:1)"),
            stderrLine("java.lang.IllegalArgumentException: second"),
            stderrLine("\tat net.example.Foo.b(Foo.java:2)")
        );

        // execute
        final List<ServerErrorSnapshot> snapshots = ServerStderrErrorScanner.scan(lines);

        // verify
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).throwableMessage()).isEqualTo("first");
        assertThat(snapshots.get(1).throwableMessage()).isEqualTo("second");
    }

    @Test
    void scan_shouldIgnoreNonExceptionNoiseAndOrphanContinuations()
    {
        // setup — benign JVM warnings and a window that starts mid-trace must not produce snapshots
        final List<OutputLine> lines = List.of(
            stderrLine("OpenJDK 64-Bit Server VM warning: Options -Xverify:none deprecated"),
            stderrLine("\tat net.example.Orphan.frame(Orphan.java:1)"),
            stderrLine("Caused by: java.lang.RuntimeException: orphan cause"),
            stderrLine("some plain diagnostic text")
        );

        // execute
        final List<ServerErrorSnapshot> snapshots = ServerStderrErrorScanner.scan(lines);

        // verify
        assertThat(snapshots).isEmpty();
    }

    @Test
    void scan_shouldTerminateBlockOnNonContinuationLine()
    {
        // setup
        final List<OutputLine> lines = List.of(
            stderrLine("java.lang.IllegalStateException: first"),
            stderrLine("\tat net.example.Foo.a(Foo.java:1)"),
            stderrLine("unrelated diagnostic output"),
            stderrLine("\tat net.example.Foo.b(Foo.java:2)")
        );

        // execute
        final List<ServerErrorSnapshot> snapshots = ServerStderrErrorScanner.scan(lines);

        // verify — the trailing orphan frame after the block ended is not attached to anything
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().stackTrace()).hasSize(2);
    }

    @Test
    void scan_shouldCapOverlongBlocksWithTruncationMarker()
    {
        // setup
        final List<OutputLine> lines = Stream.concat(
            Stream.of(stderrLine("java.lang.RuntimeException: deep")),
            IntStream.range(0, ServerStderrErrorScanner.MAX_BLOCK_LINES + 25)
                .mapToObj(i -> stderrLine("\tat net.example.Deep.frame" + i + "(Deep.java:" + i + ")"))
        ).toList();

        // execute
        final List<ServerErrorSnapshot> snapshots = ServerStderrErrorScanner.scan(lines);

        // verify
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().stackTrace()).hasSize(ServerStderrErrorScanner.MAX_BLOCK_LINES + 1);
        assertThat(snapshots.getFirst().stackTrace().getLast()).contains("truncated");
    }

    @Test
    void scan_shouldReturnEmptyListForEmptyInput()
    {
        // execute + verify
        assertThat(ServerStderrErrorScanner.scan(List.of())).isEmpty();
    }
}
