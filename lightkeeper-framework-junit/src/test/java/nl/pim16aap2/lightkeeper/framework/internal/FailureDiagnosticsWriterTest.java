package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import nl.pim16aap2.lightkeeper.framework.ServerErrorsHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FailureDiagnosticsWriterTest
{
    @Test
    void write_shouldCreateBundleWithOutcomeErrorsAndOutput(@TempDir Path tempDirectory)
        throws IOException
    {
        // setup
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        final ServerErrorsHandle serverErrorsHandle = mock(ServerErrorsHandle.class);
        when(framework.serverErrors()).thenReturn(serverErrorsHandle);
        when(serverErrorsHandle.getCaptured()).thenReturn(List.of(new ServerErrorSnapshot(
            123L,
            ServerErrorSnapshot.Severity.ERROR,
            "ERROR",
            "test.logger",
            "main",
            "boom happened",
            "java.lang.IllegalStateException",
            "boom",
            List.of("at example.Foo.bar(Foo.java:1)")
        )));
        when(framework.serverOutput()).thenReturn(List.of("line one", "line two"));
        final RuntimeException failure = new RuntimeException("test assertion failed");

        // execute
        final Path writtenDirectory = FailureDiagnosticsWriter.write(
            framework, tempDirectory, "MyTestClass", "myTestMethod", failure);

        // verify
        assertThat(writtenDirectory).isNotNull();
        final Path bundleDirectory = java.util.Objects.requireNonNull(writtenDirectory);
        assertThat(bundleDirectory).isDirectory();
        assertThat(java.util.Objects.requireNonNull(bundleDirectory.getParent()).getFileName().toString())
            .isEqualTo("MyTestClass");
        assertThat(bundleDirectory.getFileName().toString()).startsWith("myTestMethod-");
        assertThat(Files.readString(bundleDirectory.resolve("outcome.txt")))
            .contains("test: MyTestClass.myTestMethod")
            .contains("outcome: FAILED")
            .contains("test assertion failed");
        assertThat(Files.readString(bundleDirectory.resolve("server-errors.txt")))
            .contains("[ERROR] test.logger (thread: main): boom happened")
            .contains("at example.Foo.bar(Foo.java:1)");
        assertThat(Files.readString(bundleDirectory.resolve("server-output.log")))
            .contains("line one")
            .contains("line two");
    }

    @Test
    void write_shouldReportPassedOutcomeWhenNoFailureGiven(@TempDir Path tempDirectory)
        throws IOException
    {
        // setup
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        final ServerErrorsHandle serverErrorsHandle = mock(ServerErrorsHandle.class);
        when(framework.serverErrors()).thenReturn(serverErrorsHandle);
        when(serverErrorsHandle.getCaptured()).thenReturn(List.of());
        when(framework.serverOutput()).thenReturn(List.of());

        // execute
        final Path writtenDirectory = FailureDiagnosticsWriter.write(
            framework, tempDirectory, "MyTestClass", "myPassingMethod", null);

        // verify
        assertThat(writtenDirectory).isNotNull();
        final Path bundleDirectory = java.util.Objects.requireNonNull(writtenDirectory);
        assertThat(Files.readString(bundleDirectory.resolve("outcome.txt")))
            .contains("outcome: PASSED")
            .doesNotContain("FAILED");
        assertThat(Files.readString(bundleDirectory.resolve("server-errors.txt")))
            .contains("No captured server errors.");
    }

    @Test
    void write_shouldReportAbortedOutcomeForAssumptionAborts(@TempDir Path tempDirectory)
        throws IOException
    {
        // setup
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        final ServerErrorsHandle serverErrorsHandle = mock(ServerErrorsHandle.class);
        when(framework.serverErrors()).thenReturn(serverErrorsHandle);
        when(serverErrorsHandle.getCaptured()).thenReturn(List.of());
        when(framework.serverOutput()).thenReturn(List.of());

        // execute
        final Path writtenDirectory = FailureDiagnosticsWriter.write(
            framework,
            tempDirectory,
            "MyTestClass",
            "myAbortedMethod",
            new org.opentest4j.TestAbortedException("skipped on this platform"));

        // verify
        assertThat(writtenDirectory).isNotNull();
        final Path bundleDirectory = java.util.Objects.requireNonNull(writtenDirectory);
        assertThat(Files.readString(bundleDirectory.resolve("outcome.txt")))
            .contains("outcome: ABORTED")
            .contains("skipped on this platform");
    }

    @Test
    void write_shouldRecordSectionFailureWithoutLosingOtherSections(@TempDir Path tempDirectory)
        throws IOException
    {
        // setup
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        when(framework.serverErrors()).thenThrow(new IllegalStateException("agent connection is gone"));
        when(framework.serverOutput()).thenReturn(List.of("still available"));

        // execute
        final Path writtenDirectory = FailureDiagnosticsWriter.write(
            framework, tempDirectory, "MyTestClass", "myTestMethod", new RuntimeException("failed"));

        // verify
        assertThat(writtenDirectory).isNotNull();
        final Path bundleDirectory = java.util.Objects.requireNonNull(writtenDirectory);
        assertThat(Files.readString(bundleDirectory.resolve("server-errors.txt")))
            .contains("Failed to capture this section:")
            .contains("agent connection is gone");
        assertThat(Files.readString(bundleDirectory.resolve("server-output.log")))
            .contains("still available");
    }

    @Test
    void write_shouldReturnNullInsteadOfThrowingWhenRootIsNotWritable(@TempDir Path tempDirectory)
        throws IOException
    {
        // setup
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        // A regular FILE at the reports-root path makes createDirectories fail on every platform.
        final Path blockedRoot = tempDirectory.resolve("blocked");
        Files.writeString(blockedRoot, "not a directory");

        // execute
        final Path bundleDirectory = FailureDiagnosticsWriter.write(
            framework, blockedRoot, "MyTestClass", "myTestMethod", new RuntimeException("failed"));

        // verify
        assertThat(bundleDirectory).isNull();
    }

    @Test
    void write_shouldSanitizeUnsafeCharactersInDirectoryNames(@TempDir Path tempDirectory)
    {
        // setup
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        final ServerErrorsHandle serverErrorsHandle = mock(ServerErrorsHandle.class);
        when(framework.serverErrors()).thenReturn(serverErrorsHandle);
        when(serverErrorsHandle.getCaptured()).thenReturn(List.of());
        when(framework.serverOutput()).thenReturn(List.of());

        // execute
        final Path writtenDirectory = FailureDiagnosticsWriter.write(
            framework, tempDirectory, "My/Test:Class", "my method [1]", null);

        // verify
        assertThat(writtenDirectory).isNotNull();
        final Path bundleDirectory = java.util.Objects.requireNonNull(writtenDirectory);
        assertThat(java.util.Objects.requireNonNull(bundleDirectory.getParent()).getFileName().toString())
            .isEqualTo("My_Test_Class");
        assertThat(bundleDirectory.getFileName().toString()).startsWith("my_method__1_-");
    }
}
