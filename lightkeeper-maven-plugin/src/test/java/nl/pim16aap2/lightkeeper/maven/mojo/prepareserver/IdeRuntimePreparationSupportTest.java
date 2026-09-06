package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.provisioning.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import nl.pim16aap2.lightkeeper.maven.serverprovider.ServerProvider;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscovery;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdeRuntimePreparationSupportTest
{
    @Test
    void fingerprint_shouldBeStableForEquivalentInputOrdering(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path firstPlugin = Files.writeString(tempDirectory.resolve("first.jar"), "first");
        final Path secondPlugin = Files.writeString(tempDirectory.resolve("second.jar"), "second");
        final PrepareServerExecutionContext context = context(tempDirectory, List.of());
        final PrepareServerRuntimePreparation preparation = preparation();

        // execute
        final String first = IdeRuntimePreparationSupport.fingerprint(
            tempDirectory,
            "prepare-paper",
            context,
            preparation,
            List.of(
                new ResolvedPluginArtifact(firstPlugin, "first.jar", "path:first"),
                new ResolvedPluginArtifact(secondPlugin, "second.jar", "path:second")
            ),
            null,
            1024,
            null,
            "java"
        );
        final String second = IdeRuntimePreparationSupport.fingerprint(
            tempDirectory,
            "prepare-paper",
            context,
            preparation,
            List.of(
                new ResolvedPluginArtifact(secondPlugin, "second.jar", "path:second"),
                new ResolvedPluginArtifact(firstPlugin, "first.jar", "path:first")
            ),
            null,
            1024,
            null,
            "java"
        );

        // verify
        assertThat(first).isEqualTo(second).matches("[a-f0-9]{64}");
    }

    @Test
    void fingerprint_shouldChangeWhenPluginContentChanges(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path plugin = Files.writeString(tempDirectory.resolve("plugin.jar"), "first");
        final PrepareServerExecutionContext context = context(tempDirectory, List.of());
        final PrepareServerRuntimePreparation preparation = preparation();
        final List<ResolvedPluginArtifact> plugins =
            List.of(new ResolvedPluginArtifact(plugin, "plugin.jar", "path:plugin"));
        final String first = IdeRuntimePreparationSupport.fingerprint(
            tempDirectory, "prepare-paper", context, preparation, plugins, null, 1024, null, "java");

        // execute
        Files.writeString(plugin, "second");
        final String second = IdeRuntimePreparationSupport.fingerprint(
            tempDirectory, "prepare-paper", context, preparation, plugins, null, 1024, null, "java");

        // verify
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void fingerprint_shouldChangeWhenWorldDirectoryContentsChange(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path world = Files.createDirectories(tempDirectory.resolve("world"));
        final Path marker = Files.writeString(world.resolve("marker.txt"), "first");
        final WorldInputSpec worldInput = new WorldInputSpec(
            "fixture",
            WorldInputSpec.SourceType.FOLDER,
            world,
            true,
            false,
            "NORMAL",
            "NORMAL",
            0L
        );
        final PrepareServerExecutionContext context = context(tempDirectory, List.of(worldInput));
        final String first = IdeRuntimePreparationSupport.fingerprint(
            tempDirectory, "prepare-paper", context, preparation(), List.of(), null, 1024, null, "java");

        // execute
        Files.writeString(marker, "second");
        final String second = IdeRuntimePreparationSupport.fingerprint(
            tempDirectory, "prepare-paper", context, preparation(), List.of(), null, 1024, null, "java");

        // verify
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void publish_shouldExposeOnlyCompletedPreparationAndCreateIgnoreFile(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path manifest = IdeRuntimePaths.preparationsDirectory(tempDirectory)
            .resolve("fingerprint/runtime-manifest.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{}");

        // execute
        IdeRuntimePreparationSupport.publish(
            tempDirectory,
            "prepare-paper",
            "paper",
            "fingerprint",
            manifest
        );

        // verify
        final IdeRuntimeDiscovery discovery = IdeRuntimePreparationSupport.reusableDiscovery(
            tempDirectory,
            "prepare-paper",
            "paper",
            "fingerprint"
        );
        assertThat(discovery).isNotNull();
        assertThat(discovery.runtimeManifestPath()).isEqualTo(manifest.toAbsolutePath().normalize().toString());
        assertThat(IdeRuntimePaths.stateDirectory(tempDirectory).resolve(".gitignore"))
            .hasContent("*\n!.gitignore\n");
    }

    @Test
    void reusableDiscovery_shouldRejectDifferentExecutionAndMissingManifest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path manifest = IdeRuntimePaths.preparationsDirectory(tempDirectory)
            .resolve("fingerprint/runtime-manifest.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{}");
        IdeRuntimePreparationSupport.publish(
            tempDirectory,
            "prepare-paper",
            "paper",
            "fingerprint",
            manifest
        );

        // execute
        final IdeRuntimeDiscovery wrongExecution = IdeRuntimePreparationSupport.reusableDiscovery(
            tempDirectory, "prepare-spigot", "paper", "fingerprint");
        Files.delete(manifest);
        final IdeRuntimeDiscovery missingManifest = IdeRuntimePreparationSupport.reusableDiscovery(
            tempDirectory, "prepare-paper", "paper", "fingerprint");

        // verify
        assertThat(wrongExecution).isNull();
        assertThat(missingManifest).isNull();
    }

    @Test
    void newPreparationDirectory_shouldUseStableIdentityUnlessRefreshIsRequested(@TempDir Path tempDirectory)
    {
        // setup
        final String fingerprint = "abc123";

        // execute
        final Path normal = IdeRuntimePreparationSupport.newPreparationDirectory(
            tempDirectory, fingerprint, false);
        final Path refreshed = IdeRuntimePreparationSupport.newPreparationDirectory(
            tempDirectory, fingerprint, true);

        // verify
        assertThat(normal.getFileName().toString()).isEqualTo(fingerprint);
        assertThat(refreshed.getFileName().toString()).startsWith(fingerprint + "-").isNotEqualTo(fingerprint);
    }

    private static PrepareServerExecutionContext context(Path root, List<WorldInputSpec> worlds)
    {
        return new PrepareServerExecutionContext(
            "paper",
            "latest-supported",
            root.resolve("jar-cache"),
            root.resolve("base-cache"),
            root.resolve("plugin-cache"),
            root.resolve("work"),
            root.resolve("runtime-manifest.json"),
            null,
            "LightKeeper/Test",
            worlds,
            List.of()
        );
    }

    private static PrepareServerRuntimePreparation preparation()
    {
        return new PrepareServerRuntimePreparation(
            new PrepareServerAgentMetadata("agent-sha", "agent-cache"),
            1,
            "token",
            Path.of("/tmp/lightkeeper.sock"),
            new PrepareServerResolvedServerSetup(
                mock(ServerProvider.class),
                "1.21.11",
                116L,
                "server-cache",
                1024
            )
        );
    }
}
