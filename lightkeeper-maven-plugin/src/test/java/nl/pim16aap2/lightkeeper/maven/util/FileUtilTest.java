package nl.pim16aap2.lightkeeper.maven.util;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.maven.plugin.MojoExecutionException;
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
        private Configuration configuration;

        private FileSystem fs;

        @BeforeEach
        void init()
        {
            fs = Jimfs.newFileSystem(configuration);
        }

        @AfterEach
        void cleanup()
            throws IOException
        {
            fs.close();
        }

        @Test
        void createDirectories_shouldThrowExceptionForExistingFile()
            throws IOException
        {
            // setup
            final String context = "test-text-file";
            final Path path = fs.getPath("test.txt");
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
            final String context = "test-directory";
            final Path path = fs.getPath("path", "to", "test-dir");

            // execute
            FileUtil.createDirectories(path, context);

            // verify
            assertThat(path).isDirectory();
            assertThat(Files.exists(path)).isTrue();
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
