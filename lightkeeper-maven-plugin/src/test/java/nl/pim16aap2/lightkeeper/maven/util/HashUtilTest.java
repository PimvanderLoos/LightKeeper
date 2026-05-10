package nl.pim16aap2.lightkeeper.maven.util;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void sha256_shouldThrowExceptionWhenFileDoesNotExist()
    {
        // setup
        final Path missingFile = Path.of("does-not-exist.txt");

        // execute + verify
        assertThatThrownBy(() -> HashUtil.sha256(missingFile))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Failed to compute SHA-256");
    }

    @Test
    void sha512_shouldGenerateDeterministicHashForString()
        throws MojoExecutionException
    {
        // setup
        final String input = "LightKeeper";

        // execute
        final String hash = HashUtil.sha512(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

        // verify
        assertThat(hash).isEqualTo(
            "90d2cf252faed910836b776745d54aae4e384345d57b1a26e83b77f94328ea66e11fb235090df83a9503f855842fbf7372c39c1793345051e5894723b1a62831"
        );
    }

    @Test
    void sha512_shouldGenerateDeterministicHashForFile()
        throws IOException, MojoExecutionException
    {
        // setup
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix()))
        {
            final Path file = fileSystem.getPath("test.txt");
            Files.writeString(file, "LightKeeper");

            // execute
            final String hash = HashUtil.sha512(file);

            // verify
            assertThat(hash).isEqualTo(
                "90d2cf252faed910836b776745d54aae4e384345d57b1a26e83b77f94328ea66e11fb235090df83a9503f855842fbf7372c39c1793345051e5894723b1a62831"
            );
        }
    }

    @Test
    void sha512_shouldGenerateDeterministicHashForInputStream()
        throws IOException, MojoExecutionException
    {
        // setup
        try (InputStream inputStream = new ByteArrayInputStream("LightKeeper".getBytes(StandardCharsets.UTF_8)))
        {
            // execute
            final String hash = HashUtil.sha512(inputStream);

            // verify
            assertThat(hash).isEqualTo(
                "90d2cf252faed910836b776745d54aae4e384345d57b1a26e83b77f94328ea66e11fb235090df83a9503f855842fbf7372c39c1793345051e5894723b1a62831"
            );
        }
    }

    @Test
    void sha256_shouldWrapIOExceptionFromInputStream()
    {
        // setup
        final InputStream inputStream = new InputStream()
        {
            @Override
            public int read()
                throws IOException
            {
                throw new IOException("boom");
            }
        };

        // execute + verify
        assertThatThrownBy(() -> HashUtil.sha256(inputStream))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Failed to compute SHA-256 from input stream");
    }
}
