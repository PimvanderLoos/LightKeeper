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
