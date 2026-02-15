package nl.pim16aap2.lightkeeper.maven.util;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.maven.plugin.MojoExecutionException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class FileUtilTest
{
    @Nested
    @ParameterizedClass
    @MethodSource("fileSystemConfigurationProvider")
    class FileSystemTests
    {
        @SuppressWarnings({"unused", "NotNullFieldNotInitialized"})
        @Parameter
        private @Nullable Configuration configuration;

        private @Nullable FileSystem fs;

        @BeforeEach
        void init()
        {
            fs = Jimfs.newFileSystem(Objects.requireNonNull(configuration, "configuration must be initialized."));
        }

        @AfterEach
        void cleanup()
            throws IOException
        {
            final FileSystem localFs = Objects.requireNonNull(fs, "fs must be initialized.");
            localFs.close();
        }

        @Test
        void createDirectories_shouldThrowExceptionForExistingFile()
            throws IOException
        {
            // setup
            final FileSystem localFileSystem = Objects.requireNonNull(fs, "fs must be initialized.");
            final String context = "test-text-file";
            final Path path = localFileSystem.getPath("test.txt");
            Files.createFile(path);
            assertThat(path).isRegularFile();

            // execute & verify
            assertThatExceptionOfType(MojoExecutionException.class)
                .isThrownBy(() -> FileUtil.createDirectories(path, context))
                .withMessage("The path '%s' exists but is not a directory.", path);
        }

        @Test
        void createDirectories_shouldCreateDirectory()
            throws MojoExecutionException
        {
            // setup
            final FileSystem localFileSystem = Objects.requireNonNull(fs, "fs must be initialized.");
            final String context = "test-directory";
            final Path path = localFileSystem.getPath("path", "to", "test-dir");

            // execute
            FileUtil.createDirectories(path, context);

            // verify
            assertThat(path).isDirectory();
            assertThat(Files.exists(path)).isTrue();
        }

        @Test
        void copyDirectoryRecursively_shouldCopyNestedFiles()
            throws IOException, MojoExecutionException
        {
            // setup
            final FileSystem localFileSystem = Objects.requireNonNull(fs, "fs must be initialized.");
            final Path source = localFileSystem.getPath("source");
            final Path target = localFileSystem.getPath("target");
            Files.createDirectories(source.resolve("nested"));
            Files.writeString(source.resolve("root.txt"), "root");
            Files.writeString(source.resolve("nested").resolve("child.txt"), "child");

            // execute
            FileUtil.copyDirectoryRecursively(source, target);

            // verify
            assertThat(target.resolve("root.txt")).isRegularFile().hasContent("root");
            assertThat(target.resolve("nested").resolve("child.txt")).isRegularFile().hasContent("child");
        }

        @Test
        void pruneSiblingDirectoriesOlderThan_shouldDeleteOnlyExpiredSiblings()
            throws Exception
        {
            // setup
            final FileSystem localFileSystem = Objects.requireNonNull(fs, "fs must be initialized.");
            final Path parent = localFileSystem.getPath("cache");
            final Path currentDirectory = parent.resolve("active");
            final Path expiredSibling = parent.resolve("expired");
            final Path freshSibling = parent.resolve("fresh");
            Files.createDirectories(currentDirectory);
            Files.createDirectories(expiredSibling);
            Files.createDirectories(freshSibling);
            Files.setLastModifiedTime(expiredSibling, FileTime.from(Instant.now().minusSeconds(10 * 24 * 60 * 60L)));
            Files.setLastModifiedTime(freshSibling, FileTime.from(Instant.now().minusSeconds(2 * 24 * 60 * 60L)));

            // execute
            final FileUtil.PruneResult result = FileUtil.pruneSiblingDirectoriesOlderThan(currentDirectory, 7);

            // verify
            assertThat(expiredSibling).doesNotExist();
            assertThat(freshSibling).isDirectory();
            assertThat(currentDirectory).isDirectory();
            assertThat(result.deletedDirectories()).containsExactly(expiredSibling.toAbsolutePath().normalize());
            assertThat(result.failedDirectories()).isEmpty();
        }

        @Test
        void pruneSiblingDirectoriesOlderThan_shouldKeepCurrentDirectory()
            throws Exception
        {
            // setup
            final FileSystem localFileSystem = Objects.requireNonNull(fs, "fs must be initialized.");
            final Path parent = localFileSystem.getPath("cache");
            final Path currentDirectory = parent.resolve("active");
            Files.createDirectories(currentDirectory);
            Files.setLastModifiedTime(currentDirectory, FileTime.from(Instant.now().minusSeconds(30L * 24 * 60 * 60)));

            // execute
            final FileUtil.PruneResult result = FileUtil.pruneSiblingDirectoriesOlderThan(currentDirectory, 7);

            // verify
            assertThat(currentDirectory).isDirectory();
            assertThat(result.deletedDirectories()).isEmpty();
            assertThat(result.failedDirectories()).isEmpty();
        }

        @Test
        void pruneSiblingDirectoriesOlderThan_shouldIgnoreNonDirectorySiblings()
            throws Exception
        {
            // setup
            final FileSystem localFileSystem = Objects.requireNonNull(fs, "fs must be initialized.");
            final Path parent = localFileSystem.getPath("cache");
            final Path currentDirectory = parent.resolve("active");
            final Path siblingFile = parent.resolve("note.txt");
            Files.createDirectories(currentDirectory);
            Files.writeString(siblingFile, "data");

            // execute
            final FileUtil.PruneResult result = FileUtil.pruneSiblingDirectoriesOlderThan(currentDirectory, 0);

            // verify
            assertThat(currentDirectory).isDirectory();
            assertThat(siblingFile).isRegularFile().hasContent("data");
            assertThat(result.deletedDirectories()).isEmpty();
            assertThat(result.failedDirectories()).isEmpty();
        }

        static Stream<Configuration> fileSystemConfigurationProvider()
        {
            return Stream.of(
                Configuration.unix(),
                Configuration.windows(),
                Configuration.osX()
            );
        }
    }
}
