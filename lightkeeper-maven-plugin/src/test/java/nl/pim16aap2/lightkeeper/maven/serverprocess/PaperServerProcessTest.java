package nl.pim16aap2.lightkeeper.maven.serverprocess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaperServerProcessTest
{
    @Test
    void constructor_shouldCreateInstance(@TempDir Path tempDirectory)
    {
        // setup
        final Path jarPath = tempDirectory.resolve("paper.jar");

        // execute
        final PaperServerProcess process = new PaperServerProcess(tempDirectory, jarPath, "java", null, 512);

        // verify
        assertThat(process).isNotNull();
    }
}
