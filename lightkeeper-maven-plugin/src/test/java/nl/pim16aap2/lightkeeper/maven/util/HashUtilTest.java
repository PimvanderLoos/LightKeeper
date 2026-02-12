package nl.pim16aap2.lightkeeper.maven.util;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest
{
    @Test
    void sha256_shouldGenerateDeterministicHashForString()
    {
        // setup
        final String input = "LightKeeper";

        // execute
        final String hash = HashUtil.sha256(input);

        // verify
        assertThat(hash).isEqualTo("9b3d2892049c9ae3cba25acaeeee01826f36da04369416ea94ea026ae1a58e2b");
    }

    @Test
    void sha256_shouldGenerateDeterministicHashForFile()
        throws IOException, MojoExecutionException
    {
        // setup
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix()))
        {
            final Path file = fileSystem.getPath("test.txt");
            Files.writeString(file, "LightKeeper");

            // execute
            final String hash = HashUtil.sha256(file);

            // verify
            assertThat(hash).isEqualTo("9b3d2892049c9ae3cba25acaeeee01826f36da04369416ea94ea026ae1a58e2b");
        }
    }
}
