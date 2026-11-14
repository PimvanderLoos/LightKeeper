package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.provisioning.LightkeeperEmbeddedAgent;
import nl.pim16aap2.lightkeeper.provisioning.assets.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.provisioning.assets.ResolvedPluginArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PrepareServerAssetConventionsTest
{
    @TempDir
    Path module;

    @Test
    void plugins_shouldAllowMissingConventionalDirectory()
        throws Exception
    {
        // setup
        final List<PluginArtifactSpec> explicit = List.of();

        // execute
        final var result = PrepareServerAssetConventions.plugins(module, explicit);

        // verify
        assertThat(result).isEmpty();
    }

    @Test
    void plugins_shouldDiscoverSortedJarInputsWithoutDuplicatingExplicitPath()
        throws Exception
    {
        // setup
        final Path directory = Files.createDirectories(module.resolve("src/test/lightkeeper/plugins"));
        Files.writeString(directory.resolve("z.jar"), "fixture");
        final Path first = Files.writeString(directory.resolve("a.jar"), "fixture");
        Files.writeString(directory.resolve("readme.md"), "ignored");
        final var explicit = new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.PATH, first, null, null, null, null, "jar", false, "renamed.jar"
        );

        // execute
        final var result = PrepareServerAssetConventions.plugins(module, List.of(explicit));

        // verify
        assertThat(result).extracting(PluginArtifactSpec::path).containsExactly(first, directory.resolve("z.jar"));
        assertThat(result.getFirst().renameTo()).isEqualTo("renamed.jar");
    }

    @Test
    void configOverlay_shouldAllowAbsentConvention()
        throws Exception
    {
        // setup
        final Path missing = module.resolve("src/test/lightkeeper/config");

        // execute
        final Path result = PrepareServerAssetConventions.configOverlay(module, null);

        // verify
        assertThat(result).isNull();
        assertThat(missing).doesNotExist();
    }

    @Test
    void configOverlay_shouldPreferExplicitDirectoryOverConvention()
        throws Exception
    {
        // setup
        Files.createDirectories(module.resolve("src/test/lightkeeper/config"));
        final Path explicit = Files.createDirectories(module.resolve("custom-config"));

        // execute
        final Path result = PrepareServerAssetConventions.configOverlay(module, Path.of("custom-config"));

        // verify
        assertThat(result).isEqualTo(explicit);
    }

    @Test
    void configOverlay_shouldRejectMissingExplicitDirectory()
    {
        // setup
        final Path explicit = module.resolve("missing");

        // execute
        final var result = assertThatThrownBy(() -> PrepareServerAssetConventions.configOverlay(module, explicit));

        // verify
        result.hasMessageContaining("not a directory").hasMessageContaining(explicit.toString());
    }

    @Test
    void uniquePlugins_shouldInstallSameSourceAndNameOnce()
        throws Exception
    {
        // setup
        final Path source = Files.writeString(module.resolve("plugin.jar"), "fixture");
        final var target = new ResolvedPluginArtifact(source, "plugin.jar", "target");
        final var extra = new ResolvedPluginArtifact(source, "plugin.jar", "extra");

        // execute
        final var result = PrepareServerAssetConventions.uniquePlugins(List.of(target, extra), null);

        // verify
        assertThat(result).containsExactly(target);
    }

    @Test
    void uniquePlugins_shouldRejectDifferentSourcesWithSameOutputName()
        throws Exception
    {
        // setup
        final var first = new ResolvedPluginArtifact(
            Files.writeString(module.resolve("first.jar"), "first"), "plugin.jar", "first"
        );
        final var second = new ResolvedPluginArtifact(
            Files.writeString(module.resolve("second.jar"), "second"), "Plugin.jar", "second"
        );

        // execute
        final var result = assertThatThrownBy(() -> PrepareServerAssetConventions.uniquePlugins(
            List.of(first, second), null
        ));

        // verify
        result.hasMessageContaining("Conflicting plugin output");
    }

    @Test
    void uniquePlugins_shouldProtectAgentFromFixtureReplacement()
        throws Exception
    {
        // setup
        final var replacement = new ResolvedPluginArtifact(
            Files.writeString(module.resolve("replacement.jar"), "fixture"),
            LightkeeperEmbeddedAgent.FILE_NAME,
            "fixture"
        );

        // execute
        final var result = assertThatThrownBy(() -> PrepareServerAssetConventions.uniquePlugins(
            List.of(replacement), null
        ));

        // verify
        result.hasMessageContaining("cannot replace the LightKeeper agent");
    }

    @Test
    void uniquePlugins_shouldProtectSelectedPluginFromOverlayReplacement()
        throws Exception
    {
        // setup
        final Path overlay = module.resolve("overlay");
        Files.createDirectories(overlay.resolve("plugins"));
        final Path source = Files.writeString(module.resolve("plugin.jar"), "fixture");
        Files.writeString(overlay.resolve("plugins/plugin.jar"), "replacement");
        final var target = new ResolvedPluginArtifact(source, "plugin.jar", "target");

        // execute
        final var result = assertThatThrownBy(() -> PrepareServerAssetConventions.uniquePlugins(
            List.of(target), overlay
        ));

        // verify
        result.hasMessageContaining("Config overlay cannot replace");
    }
}
