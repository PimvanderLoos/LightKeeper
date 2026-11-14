package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class IdeRuntimePathsTest
{
    @Test
    void prepareStateDirectory_shouldKeepGeneratedRuntimeInputsOutOfGit(@TempDir Path root)
        throws Exception
    {
        // setup
        final Process initialize = new ProcessBuilder("git", "init", "--quiet", root.toString()).start();
        assertThat(initialize.waitFor(5, TimeUnit.SECONDS)).isTrue();
        assertThat(initialize.exitValue()).isZero();

        // execute
        IdeRuntimePaths.prepareStateDirectory(root);
        Files.writeString(IdeRuntimePaths.stateDirectory(root).resolve("runtime.json"), "generated-test-data");
        final Process status = new ProcessBuilder("git", "status", "--porcelain", "--untracked-files=all")
            .directory(root.toFile()).redirectErrorStream(true).start();
        assertThat(status.waitFor(5, TimeUnit.SECONDS)).isTrue();
        final String output = new String(status.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // verify
        assertThat(status.exitValue()).as(output).isZero();
        assertThat(output).isEmpty();
    }

    @Test
    void prepareStateDirectory_shouldMigrateOwnedLegacyStateWithoutReplacingFiles(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path state = Files.createDirectories(IdeRuntimePaths.stateDirectory(root));
        Files.setPosixFilePermissions(state, PosixFilePermissions.fromString("rwxr-xr-x"));
        final Path input = Files.writeString(state.resolve("existing.json"), "legacy-generated-data");

        // execute
        IdeRuntimePaths.prepareStateDirectory(root);

        // verify
        assertThat(Files.getPosixFilePermissions(state))
            .isEqualTo(PosixFilePermissions.fromString("rwx------"));
        assertThat(input).hasContent("legacy-generated-data");
    }

    @Test
    void prepareStateDirectory_shouldRejectSymlinksWithoutChangingTheirTarget(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path target = Files.createDirectories(root.resolve("external"));
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.createSymbolicLink(IdeRuntimePaths.stateDirectory(root), target);

        // execute
        final Throwable failure = catchThrowable(() -> IdeRuntimePaths.prepareStateDirectory(root));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("not a directory");
        assertThat(Files.getPosixFilePermissions(target))
            .isEqualTo(PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThat(target.resolve(".gitignore")).doesNotExist();
    }

    @Test
    void prepareStateDirectory_shouldPreserveExistingIgnoreRules(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path state = IdeRuntimePaths.stateDirectory(root);
        UserOnlyDirectory.prepare(state);
        final Path ignore = Files.writeString(state.resolve(".gitignore"), "existing-project-policy\n");

        // execute
        IdeRuntimePaths.prepareStateDirectory(root);

        // verify
        assertThat(ignore).hasContent("existing-project-policy\n");
    }
}
