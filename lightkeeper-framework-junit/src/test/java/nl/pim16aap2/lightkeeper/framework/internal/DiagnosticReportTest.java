package nl.pim16aap2.lightkeeper.framework.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class DiagnosticReportTest
{
    @TempDir
    private Path directory;

    @Test
    void write_shouldEscapeUntrustedTextAndRedactCopiedCredentialsWhilePreservingOriginalLogs()
        throws Exception
    {
        // setup
        final String log = "<script>alert('unsafe')</script>\npassword=private-value\n"
            + "https://user:private-password@example.org/server.jar\nAuthorization: Bearer private-bearer\n"
            + "-Dlightkeeper.authToken=private-token\n\"token\": \"private-json\" password=\"private spaced\"\n";
        Files.writeString(directory.resolve("maven.log"), log);
        final var failure = new IllegalStateException("Compilation failed in <unsafe>.java:7");

        // execute
        final Path report = DiagnosticReport.write(directory, "Compiler", "<img src=x>", failure, "Fix <source>.");

        // verify
        assertThat(report).isNotNull();
        final String html = Files.readString(directory.resolve("index.html"));
        assertThat(html).contains("&lt;img src=x&gt;", "&lt;source&gt;", "href=\"maven.log\"", "[redacted]")
            .doesNotContain("<script>", "<img", "private-value", "private-password", "private-bearer", "private-token", "private-json", "private spaced");
        assertThat(Files.readString(directory.resolve("maven.log"))).isEqualTo(log);
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void write_shouldBoundLogExcerptsAndExcludeSymlinksAndRuntimeMetadata()
        throws Exception
    {
        // setup
        Files.writeString(directory.resolve("maven.log"), "old-first-line\n" + "x".repeat(100_000) + "\nlast-error");
        final Path privateFile = Files.writeString(directory.resolve("private.txt"), "must-not-be-copied");
        Files.createSymbolicLink(directory.resolve("server-output.log"), privateFile);
        Files.writeString(directory.resolve("runtime-manifest.json"), "private-runtime-token");

        // execute
        DiagnosticReport.write(directory, "Build", "module", new IllegalStateException("failed"), "Fix source.");

        // verify
        final String html = Files.readString(directory.resolve("index.html"));
        assertThat(html).contains("last-error").hasSizeLessThan(70_000)
            .doesNotContain("old-first-line", "must-not-be-copied", "private-runtime-token", "server-output.log");
    }

    @Test
    void write_shouldRemainBestEffortWhenOverviewCannotBeCreated()
        throws Exception
    {
        // setup
        final Path file = Files.writeString(directory.resolve("file"), "original");
        final var failure = new IllegalStateException("original failure");

        // execute
        final Path report = DiagnosticReport.write(file, "Build", "module", failure, "Fix source.");

        // verify
        assertThat(report).isNull();
        assertThat(Files.readString(file)).isEqualTo("original");
        assertThat(failure).hasMessage("original failure");
    }
}
