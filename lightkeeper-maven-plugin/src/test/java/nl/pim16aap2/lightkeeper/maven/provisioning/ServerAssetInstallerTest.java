package nl.pim16aap2.lightkeeper.maven.provisioning;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerAssetInstallerTest
{
    @Test
    void installWorlds_shouldCopyWorldFolderWhenSourceTypeIsFolder(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final Path sourceWorldDirectory = tempDirectory.resolve("world-source");
        Files.createDirectories(sourceWorldDirectory.resolve("fixtures"));
        Files.writeString(sourceWorldDirectory.resolve("fixtures/marker.txt"), "fixture");

        final WorldInputSpec worldInputSpec = new WorldInputSpec(
            "fixture-world",
            WorldInputSpec.SourceType.FOLDER,
            sourceWorldDirectory,
            true,
            false,
            "NORMAL",
            "NORMAL",
            0L
        );

        // execute
        ServerAssetInstaller.installWorlds(targetServerDirectory, List.of(worldInputSpec), new SystemStreamLog());

        // verify
        assertThat(targetServerDirectory.resolve("fixture-world/fixtures/marker.txt"))
            .isRegularFile()
            .hasContent("fixture");
    }

    @Test
    void installWorlds_shouldExtractZipWhenSourceTypeIsArchive(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final Path archivePath = tempDirectory.resolve("world.zip");
        createWorldArchive(
            archivePath,
            List.of(
                new ArchiveEntry("zipped-world/", null),
                new ArchiveEntry("zipped-world/level.dat", "dummy-level"),
                new ArchiveEntry("zipped-world/data/marker.txt", "zipped")
            )
        );

        final WorldInputSpec worldInputSpec = new WorldInputSpec(
            "imported-world",
            WorldInputSpec.SourceType.ARCHIVE,
            archivePath,
            true,
            false,
            "NORMAL",
            "NORMAL",
            0L
        );

        // execute
        ServerAssetInstaller.installWorlds(targetServerDirectory, List.of(worldInputSpec), new SystemStreamLog());

        // verify
        assertThat(targetServerDirectory.resolve("imported-world/data/marker.txt"))
            .isRegularFile()
            .hasContent("zipped");
    }

    @Test
    void installWorlds_shouldThrowExceptionWhenArchiveContainsTraversalEntries(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final Path archivePath = tempDirectory.resolve("bad-world.zip");
        createWorldArchive(
            archivePath,
            List.of(
                new ArchiveEntry("../outside.txt", "bad")
            )
        );

        final WorldInputSpec worldInputSpec = new WorldInputSpec(
            "bad-world",
            WorldInputSpec.SourceType.ARCHIVE,
            archivePath,
            true,
            false,
            "NORMAL",
            "NORMAL",
            0L
        );

        // execute
        final var thrown = assertThatThrownBy(
            () -> ServerAssetInstaller.installWorlds(targetServerDirectory, List.of(worldInputSpec), new SystemStreamLog())
        );

        // verify
        thrown.isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("escapes root");
    }

    @Test
    void applyConfigOverlay_shouldReplaceExistingFiles(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final Path existingConfig = targetServerDirectory.resolve("plugins/example/config.yml");
        Files.createDirectories(existingConfig.getParent());
        Files.writeString(existingConfig, "value: old");

        final Path overlayDirectory = tempDirectory.resolve("overlay");
        final Path overlayConfig = overlayDirectory.resolve("plugins/example/config.yml");
        Files.createDirectories(overlayConfig.getParent());
        Files.writeString(overlayConfig, "value: new");

        // execute
        ServerAssetInstaller.applyConfigOverlay(overlayDirectory, targetServerDirectory, new SystemStreamLog());

        // verify
        assertThat(existingConfig).hasContent("value: new");
    }

    @Test
    void installPluginArtifacts_shouldThrowExceptionWhenOutputNameIsDuplicate(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final Path pluginOne = tempDirectory.resolve("plugin-one.jar");
        final Path pluginTwo = tempDirectory.resolve("plugin-two.jar");
        Files.writeString(pluginOne, "one");
        Files.writeString(pluginTwo, "two");

        final List<ResolvedPluginArtifact> resolvedPluginArtifacts = List.of(
            new ResolvedPluginArtifact(pluginOne, "duplicate.jar", "path:one"),
            new ResolvedPluginArtifact(pluginTwo, "duplicate.jar", "path:two")
        );

        // execute
        final var thrown = assertThatThrownBy(
            () -> ServerAssetInstaller.installPluginArtifacts(
                targetServerDirectory,
                resolvedPluginArtifacts,
                new SystemStreamLog()
            )
        );

        // verify
        thrown.isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Duplicate plugin output filename");
    }

    @Test
    void installPluginArtifacts_shouldCopyResolvedPlugins(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final Path pluginJar = tempDirectory.resolve("plugin.jar");
        Files.writeString(pluginJar, "plugin");
        final List<ResolvedPluginArtifact> resolvedPluginArtifacts = List.of(
            new ResolvedPluginArtifact(pluginJar, "my-plugin.jar", "path:plugin")
        );

        // execute
        ServerAssetInstaller.installPluginArtifacts(targetServerDirectory, resolvedPluginArtifacts, new SystemStreamLog());

        // verify
        assertThat(targetServerDirectory.resolve("plugins/my-plugin.jar")).isRegularFile().hasContent("plugin");
    }

    @Test
    void installWorlds_shouldThrowExceptionWhenTargetExistsAndOverwriteIsDisabled(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final Path sourceWorldDirectory = Files.createDirectories(tempDirectory.resolve("world-source"));
        Files.writeString(sourceWorldDirectory.resolve("level.dat"), "level");
        Files.createDirectories(targetServerDirectory.resolve("existing-world"));

        final WorldInputSpec worldInputSpec = new WorldInputSpec(
            "existing-world",
            WorldInputSpec.SourceType.FOLDER,
            sourceWorldDirectory,
            false,
            false,
            "NORMAL",
            "NORMAL",
            0L
        );

        // execute + verify
        assertThatThrownBy(
            () -> ServerAssetInstaller.installWorlds(targetServerDirectory, List.of(worldInputSpec), new SystemStreamLog())
        )
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("overwrite=false");
    }

    @Test
    void installWorlds_shouldThrowExceptionWhenArchiveIsAmbiguous(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final Path archivePath = tempDirectory.resolve("world.zip");
        createWorldArchive(
            archivePath,
            List.of(
                new ArchiveEntry("a/", null),
                new ArchiveEntry("b/", null)
            )
        );
        final WorldInputSpec worldInputSpec = new WorldInputSpec(
            "ambiguous-world",
            WorldInputSpec.SourceType.ARCHIVE,
            archivePath,
            true,
            false,
            "NORMAL",
            "NORMAL",
            0L
        );

        // execute + verify
        assertThatThrownBy(
            () -> ServerAssetInstaller.installWorlds(targetServerDirectory, List.of(worldInputSpec), new SystemStreamLog())
        )
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("ambiguous");
    }

    @Test
    void applyConfigOverlay_shouldThrowExceptionWhenOverlayPathDoesNotExist(@TempDir Path tempDirectory)
    {
        // setup
        final Path overlayDirectory = tempDirectory.resolve("missing-overlay");
        final Path targetServerDirectory = tempDirectory.resolve("server");

        // execute + verify
        assertThatThrownBy(() -> ServerAssetInstaller.applyConfigOverlay(
            overlayDirectory,
            targetServerDirectory,
            new SystemStreamLog()
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("does not exist");
    }

    @Test
    void applyConfigOverlay_shouldThrowExceptionWhenOverlayPathIsNotDirectory(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path overlayFile = tempDirectory.resolve("overlay.txt");
        Files.writeString(overlayFile, "overlay");
        final Path targetServerDirectory = tempDirectory.resolve("server");

        // execute + verify
        assertThatThrownBy(() -> ServerAssetInstaller.applyConfigOverlay(
            overlayFile,
            targetServerDirectory,
            new SystemStreamLog()
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("is not a directory");
    }

    @Test
    void installPluginArtifacts_shouldReturnWhenPluginListIsEmpty(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");

        // execute
        ServerAssetInstaller.installPluginArtifacts(targetServerDirectory, List.of(), new SystemStreamLog());

        // verify
        assertThat(targetServerDirectory.resolve("plugins")).doesNotExist();
    }

    @Test
    void installPluginArtifacts_shouldThrowExceptionWhenSourceJarDoesNotExist(@TempDir Path tempDirectory)
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final ResolvedPluginArtifact resolvedPluginArtifact = new ResolvedPluginArtifact(
            tempDirectory.resolve("missing.jar"),
            "missing.jar",
            "path:missing"
        );

        // execute + verify
        assertThatThrownBy(() -> ServerAssetInstaller.installPluginArtifacts(
            targetServerDirectory,
            List.of(resolvedPluginArtifact),
            new SystemStreamLog()
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("does not exist as a regular file");
    }

    @Test
    void installPluginArtifacts_shouldThrowExceptionWhenOutputNameIsInvalid(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final Path pluginJar = tempDirectory.resolve("plugin.jar");
        Files.writeString(pluginJar, "plugin");
        final ResolvedPluginArtifact resolvedPluginArtifact = new ResolvedPluginArtifact(
            pluginJar,
            "bad/name.jar",
            "path:plugin"
        );

        // execute + verify
        assertThatThrownBy(() -> ServerAssetInstaller.installPluginArtifacts(
            targetServerDirectory,
            List.of(resolvedPluginArtifact),
            new SystemStreamLog()
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("may not contain path separators");
    }

    @Test
    void installWorlds_shouldThrowExceptionWhenFolderSourceIsNotDirectory(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final Path sourceFile = tempDirectory.resolve("world-source.txt");
        Files.writeString(sourceFile, "not-a-directory");
        final WorldInputSpec worldInputSpec = new WorldInputSpec(
            "fixture-world",
            WorldInputSpec.SourceType.FOLDER,
            sourceFile,
            true,
            false,
            "NORMAL",
            "NORMAL",
            0L
        );

        // execute + verify
        assertThatThrownBy(() -> ServerAssetInstaller.installWorlds(
            targetServerDirectory,
            List.of(worldInputSpec),
            new SystemStreamLog()
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("is not a directory");
    }

    @Test
    void installWorlds_shouldThrowExceptionWhenArchiveFileIsMissing(@TempDir Path tempDirectory)
    {
        // setup
        final Path targetServerDirectory = tempDirectory.resolve("server");
        final WorldInputSpec worldInputSpec = new WorldInputSpec(
            "missing-world",
            WorldInputSpec.SourceType.ARCHIVE,
            tempDirectory.resolve("missing.zip"),
            true,
            false,
            "NORMAL",
            "NORMAL",
            0L
        );

        // execute + verify
        assertThatThrownBy(() -> ServerAssetInstaller.installWorlds(
            targetServerDirectory,
            List.of(worldInputSpec),
            new SystemStreamLog()
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("does not exist");
    }

    private static void createWorldArchive(Path archivePath, List<ArchiveEntry> entries)
        throws IOException
    {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(archivePath)))
        {
            for (ArchiveEntry entry : entries)
            {
                zipOutputStream.putNextEntry(new ZipEntry(entry.path()));
                if (entry.content() != null)
                    zipOutputStream.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
    }

    private record ArchiveEntry(String path, @Nullable String content)
    {
    }
}
