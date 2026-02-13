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
