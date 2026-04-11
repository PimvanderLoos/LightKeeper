package nl.pim16aap2.lightkeeper.maven.mojo.cleanupserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CleanupServerMojoTest
{
    @Test
    void execute_shouldDeleteServerDirectoryWhenEnabledAndSummaryIsSuccessful(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = tempDirectory.resolve("lightkeeper-server");
        Files.createDirectories(serverDirectory);
        Files.writeString(serverDirectory.resolve("marker.txt"), "marker");

        final Path summaryPath = tempDirectory.resolve("failsafe-summary.xml");
        Files.writeString(summaryPath, """
            <failsafe-summary result="null" timeout="false">
                <completed>1</completed>
                <errors>0</errors>
                <failures>0</failures>
                <skipped>0</skipped>
            </failsafe-summary>
            """);

        final CleanupServerMojo cleanupServerMojo = new CleanupServerMojo(true, serverDirectory, summaryPath);

        // execute
        cleanupServerMojo.execute();

        // verify
        assertThat(serverDirectory).doesNotExist();
    }

    @Test
    void execute_shouldKeepServerDirectoryWhenSummaryContainsFailures(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = tempDirectory.resolve("lightkeeper-server");
        Files.createDirectories(serverDirectory);
        Files.writeString(serverDirectory.resolve("marker.txt"), "marker");

        final Path summaryPath = tempDirectory.resolve("failsafe-summary.xml");
        Files.writeString(summaryPath, """
            <failsafe-summary result="null" timeout="false">
                <completed>1</completed>
                <errors>0</errors>
                <failures>1</failures>
                <skipped>0</skipped>
            </failsafe-summary>
            """);

        final CleanupServerMojo cleanupServerMojo = new CleanupServerMojo(true, serverDirectory, summaryPath);

        // execute
        cleanupServerMojo.execute();

        // verify
        assertThat(serverDirectory).isDirectory();
    }

    @Test
    void execute_shouldKeepServerDirectoryWhenCleanupIsDisabled(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = tempDirectory.resolve("lightkeeper-server");
        Files.createDirectories(serverDirectory);
        Files.writeString(serverDirectory.resolve("marker.txt"), "marker");

        final Path summaryPath = tempDirectory.resolve("failsafe-summary.xml");
        Files.writeString(summaryPath, """
            <failsafe-summary result="null" timeout="false">
                <completed>1</completed>
                <errors>0</errors>
                <failures>0</failures>
                <skipped>0</skipped>
            </failsafe-summary>
            """);

        final CleanupServerMojo cleanupServerMojo = new CleanupServerMojo(false, serverDirectory, summaryPath);

        // execute
        cleanupServerMojo.execute();

        // verify
        assertThat(serverDirectory).isDirectory();
    }

    @Test
    void execute_shouldKeepServerDirectoryWhenSummaryResultIsNotNull(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = tempDirectory.resolve("lightkeeper-server");
        Files.createDirectories(serverDirectory);
        Files.writeString(serverDirectory.resolve("marker.txt"), "marker");

        final Path summaryPath = tempDirectory.resolve("failsafe-summary.xml");
        Files.writeString(summaryPath, """
            <failsafe-summary result="254" timeout="false">
                <completed>0</completed>
                <errors>0</errors>
                <failures>0</failures>
                <skipped>0</skipped>
            </failsafe-summary>
            """);

        final CleanupServerMojo cleanupServerMojo = new CleanupServerMojo(true, serverDirectory, summaryPath);

        // execute
        cleanupServerMojo.execute();

        // verify
        assertThat(serverDirectory).isDirectory();
    }

    @Test
    void execute_shouldKeepServerDirectoryWhenSummaryTimedOut(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = tempDirectory.resolve("lightkeeper-server");
        Files.createDirectories(serverDirectory);
        Files.writeString(serverDirectory.resolve("marker.txt"), "marker");

        final Path summaryPath = tempDirectory.resolve("failsafe-summary.xml");
        Files.writeString(summaryPath, """
            <failsafe-summary result="null" timeout="true">
                <completed>1</completed>
                <errors>0</errors>
                <failures>0</failures>
                <skipped>0</skipped>
            </failsafe-summary>
            """);

        final CleanupServerMojo cleanupServerMojo = new CleanupServerMojo(true, serverDirectory, summaryPath);

        // execute
        cleanupServerMojo.execute();

        // verify
        assertThat(serverDirectory).isDirectory();
    }

    @Test
    void execute_shouldKeepServerDirectoryWhenSummaryFileIsMissing(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = tempDirectory.resolve("lightkeeper-server");
        Files.createDirectories(serverDirectory);
        final CleanupServerMojo cleanupServerMojo =
            new CleanupServerMojo(true, serverDirectory, tempDirectory.resolve("missing-summary.xml"));

        // execute
        cleanupServerMojo.execute();

        // verify
        assertThat(serverDirectory).isDirectory();
    }

    @Test
    void execute_shouldThrowExceptionWhenSummaryIsMalformed(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path serverDirectory = tempDirectory.resolve("lightkeeper-server");
        Files.createDirectories(serverDirectory);
        final Path summaryPath = tempDirectory.resolve("failsafe-summary.xml");
        Files.writeString(summaryPath, "<failsafe-summary>");
        final CleanupServerMojo cleanupServerMojo = new CleanupServerMojo(true, serverDirectory, summaryPath);

        // execute + verify
        assertThatThrownBy(cleanupServerMojo::execute)
            .hasMessageContaining("Failed to read failsafe summary");
    }

    @Test
    void execute_shouldDeleteOnlyConfiguredServerDirectoryWhenSummaryIsSuccessful(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path configuredDirectory = tempDirectory.resolve("lightkeeper-server-paper");
        final Path unrelatedDirectory = tempDirectory.resolve("lightkeeper-server-spigot");
        Files.createDirectories(configuredDirectory);
        Files.createDirectories(unrelatedDirectory);
        Files.writeString(configuredDirectory.resolve("marker.txt"), "configured");
        Files.writeString(unrelatedDirectory.resolve("marker.txt"), "unrelated");

        final Path summaryPath = tempDirectory.resolve("failsafe-summary.xml");
        Files.writeString(summaryPath, """
            <failsafe-summary result="null" timeout="false">
                <completed>1</completed>
                <errors>0</errors>
                <failures>0</failures>
                <skipped>0</skipped>
            </failsafe-summary>
            """);

        final CleanupServerMojo cleanupServerMojo = new CleanupServerMojo(true, configuredDirectory, summaryPath);

        // execute
        cleanupServerMojo.execute();

        // verify
        assertThat(configuredDirectory).doesNotExist();
        assertThat(unrelatedDirectory).isDirectory();
    }

}
