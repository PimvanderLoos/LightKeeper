package nl.pim16aap2.lightkeeper.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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

        final CleanupServerMojo cleanupServerMojo = new CleanupServerMojo();
        setField(cleanupServerMojo, "deleteTargetServerOnSuccess", true);
        setField(cleanupServerMojo, "serverWorkDirectoryRoot", serverDirectory);
        setField(cleanupServerMojo, "failsafeSummaryPath", summaryPath);

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

        final CleanupServerMojo cleanupServerMojo = new CleanupServerMojo();
        setField(cleanupServerMojo, "deleteTargetServerOnSuccess", true);
        setField(cleanupServerMojo, "serverWorkDirectoryRoot", serverDirectory);
        setField(cleanupServerMojo, "failsafeSummaryPath", summaryPath);

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

        final CleanupServerMojo cleanupServerMojo = new CleanupServerMojo();
        setField(cleanupServerMojo, "deleteTargetServerOnSuccess", false);
        setField(cleanupServerMojo, "serverWorkDirectoryRoot", serverDirectory);
        setField(cleanupServerMojo, "failsafeSummaryPath", summaryPath);

        // execute
        cleanupServerMojo.execute();

        // verify
        assertThat(serverDirectory).isDirectory();
    }

    private static void setField(Object instance, String fieldName, Object value)
        throws Exception
    {
        final Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
