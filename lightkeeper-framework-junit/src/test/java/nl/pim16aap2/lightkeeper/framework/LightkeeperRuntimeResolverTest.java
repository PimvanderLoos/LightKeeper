package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscovery;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscoveryWriter;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimePaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LightkeeperRuntimeResolverTest
{
    @AfterEach
    void clearRuntimeManifestProperty()
    {
        System.clearProperty(LightkeeperRuntimeResolver.RUNTIME_MANIFEST_PROPERTY);
    }

    @Test
    void resolveFromClassOutput_shouldPreferExplicitManifest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path explicitManifest = Files.writeString(tempDirectory.resolve("explicit.json"), "{}");
        System.setProperty(
            LightkeeperRuntimeResolver.RUNTIME_MANIFEST_PROPERTY,
            explicitManifest.toString()
        );

        // execute
        final Path resolved = LightkeeperRuntimeResolver.resolveFromClassOutput(
            tempDirectory.resolve("unsupported-output-layout")
        );

        // verify
        assertThat(resolved).isEqualTo(explicitManifest.toAbsolutePath().normalize());
    }

    @Test
    void resolveFromClassOutput_shouldNotFallBackWhenExplicitManifestIsInvalid(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path moduleDirectory = Files.createDirectories(tempDirectory.resolve("module"));
        final Path classOutput = Files.createDirectories(moduleDirectory.resolve("target/test-classes"));
        writeValidDiscovery(moduleDirectory);
        final Path missingExplicitManifest = tempDirectory.resolve("missing.json");
        System.setProperty(
            LightkeeperRuntimeResolver.RUNTIME_MANIFEST_PROPERTY,
            missingExplicitManifest.toString()
        );

        // execute + verify
        assertThatThrownBy(() -> LightkeeperRuntimeResolver.resolveFromClassOutput(classOutput))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Explicit runtime manifest")
            .hasMessageContaining(missingExplicitManifest.toString());
    }

    @Test
    void resolveFromClassOutput_shouldDiscoverModuleRuntimeWithoutExplicitProperty(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path moduleDirectory = Files.createDirectories(tempDirectory.resolve("module"));
        final Path classOutput = Files.createDirectories(moduleDirectory.resolve("target/test-classes"));
        final Path manifest = writeValidDiscovery(moduleDirectory);

        // execute
        final Path resolved = LightkeeperRuntimeResolver.resolveFromClassOutput(classOutput);

        // verify
        assertThat(resolved).isEqualTo(manifest);
    }

    private static Path writeValidDiscovery(Path moduleDirectory)
        throws Exception
    {
        final Path manifest = IdeRuntimePaths.preparationsDirectory(moduleDirectory)
            .resolve("fingerprint/runtime-manifest.json")
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{}");
        new IdeRuntimeDiscoveryWriter().write(
            new IdeRuntimeDiscovery(
                IdeRuntimeDiscovery.SCHEMA_VERSION,
                moduleDirectory.toRealPath().toString(),
                "prepare-server",
                "paper",
                manifest.toString(),
                "fingerprint"
            ),
            IdeRuntimePaths.discoveryFile(moduleDirectory)
        );
        return manifest;
    }
}
