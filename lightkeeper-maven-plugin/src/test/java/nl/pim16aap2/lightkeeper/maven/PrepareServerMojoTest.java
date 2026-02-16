package nl.pim16aap2.lightkeeper.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrepareServerMojoTest
{
    @Test
    void ensureRuntimeManifestParentDirectoryExists_shouldCreateMissingParentDirectory(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path runtimeManifestPath = tempDirectory.resolve("runtime-manifests").resolve("runtime-manifest.json");
        assertThat(runtimeManifestPath.getParent()).doesNotExist();

        // execute
        PrepareServerMojo.ensureRuntimeManifestParentDirectoryExists(runtimeManifestPath);

        // verify
        assertThat(runtimeManifestPath.getParent()).isDirectory();
    }

    @Test
    void ensureRuntimeManifestParentDirectoryExists_shouldIgnorePathsWithoutParent()
        throws Exception
    {
        // setup
        final Path runtimeManifestPath = Path.of("runtime-manifest.json");
        assertThat(runtimeManifestPath.getParent()).isNull();

        // execute
        PrepareServerMojo.ensureRuntimeManifestParentDirectoryExists(runtimeManifestPath);

        // verify
        assertThat(runtimeManifestPath.getParent()).isNull();
    }
}
