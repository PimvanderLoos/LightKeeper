package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdeRuntimeDiscoveryTest
{
    @Test
    void resolve_shouldReturnManifestOwnedByTestModule(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path moduleDirectory = Files.createDirectories(tempDirectory.resolve("module"));
        final Path classOutput = Files.createDirectories(moduleDirectory.resolve("target/test-classes"));
        final Path manifest = writeDiscovery(moduleDirectory, moduleDirectory);

        // execute
        final Path resolved = IdeRuntimeDiscoveryResolver.resolve(classOutput);

        // verify
        assertThat(resolved).isEqualTo(manifest);
    }

    @Test
    void resolve_shouldSurviveRecreatedMavenClassOutput(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path moduleDirectory = Files.createDirectories(tempDirectory.resolve("module"));
        final Path targetDirectory = Files.createDirectories(moduleDirectory.resolve("target"));
        final Path classOutput = Files.createDirectories(targetDirectory.resolve("test-classes"));
        final Path manifest = writeDiscovery(moduleDirectory, moduleDirectory);
        Files.delete(classOutput);
        Files.delete(targetDirectory);
        final Path recompiledClassOutput = Files.createDirectories(moduleDirectory.resolve("target/test-classes"));

        // execute
        final Path resolved = IdeRuntimeDiscoveryResolver.resolve(recompiledClassOutput);

        // verify
        assertThat(resolved).isEqualTo(manifest);
    }

    @Test
    void resolve_shouldSelectRuntimeFromEachOwningModule(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path paperModule = Files.createDirectories(tempDirectory.resolve("paper-module"));
        final Path spigotModule = Files.createDirectories(tempDirectory.resolve("spigot-module"));
        final Path paperOutput = Files.createDirectories(paperModule.resolve("target/test-classes"));
        final Path spigotOutput = Files.createDirectories(spigotModule.resolve("target/test-classes"));
        final Path paperManifest = writeDiscovery(paperModule, paperModule, "paper");
        final Path spigotManifest = writeDiscovery(spigotModule, spigotModule, "spigot");

        // execute
        final Path resolvedPaper = IdeRuntimeDiscoveryResolver.resolve(paperOutput);
        final Path resolvedSpigot = IdeRuntimeDiscoveryResolver.resolve(spigotOutput);

        // verify
        assertThat(resolvedPaper).isEqualTo(paperManifest);
        assertThat(resolvedSpigot).isEqualTo(spigotManifest);
    }

    @Test
    void resolve_shouldRejectDiscoveryOwnedByDifferentModule(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path moduleDirectory = Files.createDirectories(tempDirectory.resolve("module"));
        final Path otherModule = Files.createDirectories(tempDirectory.resolve("other-module"));
        final Path classOutput = Files.createDirectories(moduleDirectory.resolve("target/test-classes"));
        writeDiscovery(moduleDirectory, otherModule);

        // execute + verify
        assertThatThrownBy(() -> IdeRuntimeDiscoveryResolver.resolve(classOutput))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("belongs to module")
            .hasMessageContaining("lightkeeper.ide=true");
    }

    @Test
    void resolve_shouldRejectMissingDiscoveryWithSetupInstruction(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path classOutput = Files.createDirectories(tempDirectory.resolve("module/target/test-classes"));

        // execute + verify
        assertThatThrownBy(() -> IdeRuntimeDiscoveryResolver.resolve(classOutput))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("discovery file")
            .hasMessageContaining("lightkeeper.runtimeManifestPath");
    }

    @Test
    void resolve_shouldRejectCorruptDiscovery(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path moduleDirectory = Files.createDirectories(tempDirectory.resolve("module"));
        final Path classOutput = Files.createDirectories(moduleDirectory.resolve("target/test-classes"));
        final Path discoveryPath = IdeRuntimePaths.discoveryFile(moduleDirectory);
        Files.createDirectories(discoveryPath.getParent());
        Files.writeString(discoveryPath, "not-json");

        // execute + verify
        assertThatThrownBy(() -> IdeRuntimeDiscoveryResolver.resolve(classOutput))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("corrupt")
            .hasMessageContaining(discoveryPath.toString());
    }

    @Test
    void resolveModuleDirectory_shouldRejectUnsupportedOutputLayout(@TempDir Path tempDirectory)
    {
        // setup
        final Path customOutput = tempDirectory.resolve("out/test/module");

        // execute + verify
        assertThatThrownBy(() -> IdeRuntimeDiscoveryResolver.resolveModuleDirectory(customOutput))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unsupported test class output layout")
            .hasMessageContaining("lightkeeper.runtimeManifestPath");
    }

    @Test
    void writer_shouldAtomicallyReplaceDiscovery(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path discoveryPath = tempDirectory.resolve("discovery.json");
        final IdeRuntimeDiscovery first = discovery(tempDirectory, "paper", "first");
        final IdeRuntimeDiscovery second = discovery(tempDirectory, "spigot", "second");
        final IdeRuntimeDiscoveryWriter writer = new IdeRuntimeDiscoveryWriter();
        writer.write(first, discoveryPath);

        // execute
        writer.write(second, discoveryPath);

        // verify
        assertThat(new IdeRuntimeDiscoveryReader().read(discoveryPath)).isEqualTo(second);
        assertThat(discoveryPath.getParent()).isDirectoryContaining(path ->
            path.getFileName().toString().equals(IdeRuntimePaths.DISCOVERY_FILE_NAME));
    }

    private static Path writeDiscovery(Path moduleDirectory, Path recordedModule)
        throws Exception
    {
        return writeDiscovery(moduleDirectory, recordedModule, "paper");
    }

    private static Path writeDiscovery(Path moduleDirectory, Path recordedModule, String serverType)
        throws Exception
    {
        final Path manifest = IdeRuntimePaths.preparationsDirectory(moduleDirectory)
            .resolve("fingerprint/runtime-manifest.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{}");
        new IdeRuntimeDiscoveryWriter().write(
            new IdeRuntimeDiscovery(
                IdeRuntimeDiscovery.SCHEMA_VERSION,
                recordedModule.toRealPath().toString(),
                "prepare-server-paper",
                serverType,
                manifest.toAbsolutePath().normalize().toString(),
                "fingerprint"
            ),
            IdeRuntimePaths.discoveryFile(moduleDirectory)
        );
        return manifest.toAbsolutePath().normalize();
    }

    private static IdeRuntimeDiscovery discovery(Path moduleDirectory, String serverType, String fingerprint)
    {
        return new IdeRuntimeDiscovery(
            IdeRuntimeDiscovery.SCHEMA_VERSION,
            moduleDirectory.toAbsolutePath().normalize().toString(),
            "prepare-server",
            serverType,
            moduleDirectory.resolve("runtime-manifest.json").toString(),
            fingerprint
        );
    }
}
