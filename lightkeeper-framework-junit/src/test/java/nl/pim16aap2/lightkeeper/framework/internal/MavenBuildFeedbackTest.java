package nl.pim16aap2.lightkeeper.framework.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MavenBuildFeedbackTest
{
    @TempDir
    private Path directory;

    @Test
    void drain_shouldPublishActualPhasesBeforeProcessExitAndRetainFirstUsefulError()
        throws Exception
    {
        // setup
        final Path log = Files.writeString(directory.resolve("maven.log"),
            "[INFO] --- compiler:3.15.0:compile (default-compile) @ producer ---\n");
        final List<String> messages = new ArrayList<>();
        final var feedback = new MavenBuildFeedback(log, messages::add);

        // execute
        feedback.drain();
        final List<String> beforeExit = List.copyOf(messages);
        Files.writeString(log, "[ERROR] COMPILATION ERROR :\n[ERROR] ----\n"
            + "[ERROR] /source/Plugin.java:[7,4] invalid symbol\n[ERROR] later generic Maven failure\n",
            StandardOpenOption.APPEND);
        feedback.finish();

        // verify
        assertThat(beforeExit).containsExactly("LK_PROGRESS: Compiling production inputs — producer");
        assertThat(feedback.problem()).isEqualTo("/source/Plugin.java:[7,4] invalid symbol");
        assertThat(feedback.phase()).isEqualTo("Compiling production inputs — producer");
        assertThat(messages).isEqualTo(beforeExit);
    }

    @Test
    void drain_shouldPreservePartialUtf8LinesAndDeduplicateRepeatedProgress()
        throws Exception
    {
        // setup
        final Path log = directory.resolve("maven.log");
        final byte[] line = "INFO: LK_PHASE: Reusing cache — ready\n".getBytes(StandardCharsets.UTF_8);
        final int split = "INFO: LK_PHASE: Reusing cache ".getBytes(StandardCharsets.UTF_8).length + 1;
        Files.write(log, Arrays.copyOfRange(line, 0, split));
        final List<String> messages = new ArrayList<>();
        final var feedback = new MavenBuildFeedback(log, messages::add);

        // execute
        feedback.drain();
        final boolean initiallyEmpty = messages.isEmpty();
        Files.write(log, Arrays.copyOfRange(line, split, line.length), StandardOpenOption.APPEND);
        feedback.drain();
        Files.write(log, line, StandardOpenOption.APPEND);
        feedback.finish();

        // verify
        assertThat(initiallyEmpty).isTrue();
        assertThat(messages).containsExactly("LK_PROGRESS: Reusing cache — ready");
    }

    @Test
    void finish_shouldBoundHugeLinesCatchFinalErrorsAndRedactCopiedSecrets()
        throws Exception
    {
        // setup
        final Path log = Files.writeString(directory.resolve("maven.log"), "x".repeat(400_000)
            + "\n[ERROR] Download https://user:private-password@example.org/server.jar token=private-token");
        final var feedback = new MavenBuildFeedback(log, ignored -> { });

        // execute
        feedback.finish();

        // verify
        assertThat(feedback.problem()).contains("Download", "[redacted]")
            .doesNotContain("private-password", "private-token");
    }

    @Test
    void drain_shouldTolerateAbsentOrTruncatedLogsWithoutChangingBuildResult()
        throws Exception
    {
        // setup
        final Path log = directory.resolve("maven.log");
        final List<String> messages = new ArrayList<>();
        final var feedback = new MavenBuildFeedback(log, messages::add);

        // execute
        feedback.drain();
        Files.writeString(log, "ordinary output\n".repeat(30));
        feedback.drain();
        Files.writeString(log, "LK_PHASE: Ready\n");
        feedback.finish();

        // verify
        assertThat(messages).containsExactly("LK_PROGRESS: Ready");
        assertThat(feedback.problem()).isNull();
    }
}
