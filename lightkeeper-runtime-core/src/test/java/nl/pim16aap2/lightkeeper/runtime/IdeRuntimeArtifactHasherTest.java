package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class IdeRuntimeArtifactHasherTest
{
    @Test
    void sha256_shouldRejectMissingInputAndNestedSymbolicLinks(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path directory = Files.createDirectories(root.resolve("input"));
        final Path target = Files.writeString(root.resolve("target.txt"), "target");
        Files.createSymbolicLink(directory.resolve("link.txt"), target);

        // execute
        final Throwable missing = catchThrowable(() -> IdeRuntimeArtifactHasher.sha256(root.resolve("missing")));
        final Throwable linked = catchThrowable(() -> IdeRuntimeArtifactHasher.sha256(directory));

        // verify
        assertThat(missing).isInstanceOf(IOException.class).hasMessageContaining("does not exist");
        assertThat(linked).isInstanceOf(IOException.class).hasMessageContaining("Symbolic links are not allowed");
    }

    @Test
    void sha256_shouldIncludeRelativeNamesAndFileContents(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path directory = Files.createDirectories(root.resolve("input"));
        final Path file = Files.writeString(directory.resolve("first.txt"), "first");
        final String original = IdeRuntimeArtifactHasher.sha256(directory);

        // execute
        Files.writeString(file, "second");
        final String changedContents = IdeRuntimeArtifactHasher.sha256(directory);
        Files.move(file, directory.resolve("renamed.txt"));
        final String changedName = IdeRuntimeArtifactHasher.sha256(directory);

        // verify
        assertThat(changedContents).isNotEqualTo(original);
        assertThat(changedName).isNotEqualTo(changedContents);
    }
}
