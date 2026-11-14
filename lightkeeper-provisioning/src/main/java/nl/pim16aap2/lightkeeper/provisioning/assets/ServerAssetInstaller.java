package nl.pim16aap2.lightkeeper.provisioning.assets;

import java.lang.System.Logger.Level;
import java.lang.System.Logger;
import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import nl.pim16aap2.lightkeeper.provisioning.util.FileUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Applies worlds, plugin artifacts, and config overlays to a prepared target server directory.
 */
public final class ServerAssetInstaller
{
    private ServerAssetInstaller()
    {
    }

    /**
     * Installs the selected world folders or guarded archives.
     *
     * @param targetServerDirectory
     *     Destination server directory.
     * @param worlds
     *     Explicitly selected world inputs.
     * @param log
     *     Provisioning log sink.
     * @throws ProvisioningException
     *     If an input is invalid or cannot be installed.
     */
    public static void installWorlds(Path targetServerDirectory, List<WorldInputSpec> worlds, Logger log)
        throws ProvisioningException
    {
        for (final WorldInputSpec world : worlds)
        {
            final long startTimeNanos = System.nanoTime();
            final Path targetWorldDirectory = resolveChildPath(targetServerDirectory, world.name(), "world name");

            log.log(Level.INFO, "LK_WORLD: Installing world '%s' from '%s' (%s)."
                .formatted(world.name(), world.sourcePath(), world.sourceType()));

            if (Files.exists(targetWorldDirectory))
            {
                if (!world.overwrite())
                {
                    throw new ProvisioningException(
                        "World target directory '%s' already exists and overwrite=false for world '%s'."
                            .formatted(targetWorldDirectory, world.name())
                    );
                }
                FileUtil.deleteRecursively(targetWorldDirectory, "target world directory");
            }

            switch (world.sourceType())
            {
                case FOLDER -> installWorldFromFolder(world, targetWorldDirectory);
                case ARCHIVE -> installWorldFromArchive(world, targetWorldDirectory);
            }

            final long durationMillis = (System.nanoTime() - startTimeNanos) / 1_000_000L;
            log.log(Level.INFO, "LK_WORLD: Installed world '%s' to '%s' in %d ms."
                .formatted(world.name(), targetWorldDirectory, durationMillis));
        }
    }

    /**
     * Copies selected plugin JARs without replacing existing plugin files.
     *
     * @param targetServerDirectory
     *     Destination server directory.
     * @param pluginArtifacts
     *     Resolved plugin files and deployment names.
     * @param log
     *     Provisioning log sink.
     * @throws ProvisioningException
     *     If a file is missing, conflicts, or cannot be copied.
     */
    public static void installPluginArtifacts(
        Path targetServerDirectory,
        List<ResolvedPluginArtifact> pluginArtifacts,
        Logger log
    )
        throws ProvisioningException
    {
        if (pluginArtifacts.isEmpty())
            return;

        final Path pluginsDirectory = targetServerDirectory.resolve("plugins");
        FileUtil.createDirectories(pluginsDirectory, "plugins directory");

        final Set<String> usedFileNames = new HashSet<>();
        for (final ResolvedPluginArtifact pluginArtifact : pluginArtifacts)
        {
            final Path sourceJar = pluginArtifact.sourceJar();
            if (!Files.isRegularFile(sourceJar))
            {
                throw new ProvisioningException(
                    "Resolved plugin artifact '%s' does not exist as a regular file."
                        .formatted(sourceJar)
                );
            }

            final String outputFileName = validatePluginOutputFileName(pluginArtifact.outputFileName());
            final String outputFileNameLowerCase = outputFileName.toLowerCase(Locale.ROOT);
            if (!usedFileNames.add(outputFileNameLowerCase))
            {
                throw new ProvisioningException(
                    "Duplicate plugin output filename '%s' detected while installing additional plugins."
                        .formatted(outputFileName)
                );
            }

            final Path targetPath = pluginsDirectory.resolve(outputFileName);
            if (Files.exists(targetPath))
            {
                throw new ProvisioningException(
                    "Plugin target path '%s' already exists. Use a unique renameTo value."
                        .formatted(targetPath)
                );
            }
            log.log(Level.INFO, "LK_PLUGIN: Copying plugin '%s' from '%s' to '%s'."
                .formatted(pluginArtifact.sourceDescription(), sourceJar, targetPath));

            try
            {
                Files.copy(sourceJar, targetPath);
            }
            catch (IOException exception)
            {
                throw new ProvisioningException(
                    "Failed to copy plugin artifact from '%s' to '%s'."
                        .formatted(sourceJar, targetPath),
                    exception
                );
            }
        }
    }

    /**
     * Applies a configuration tree while rejecting links and escaping paths.
     *
     * @param configOverlayPath
     *     Explicit or conventional configuration directory.
     * @param targetServerDirectory
     *     Destination server directory.
     * @param log
     *     Provisioning log sink.
     * @throws ProvisioningException
     *     If the overlay is invalid or copying fails.
     */
    public static void applyConfigOverlay(Path configOverlayPath, Path targetServerDirectory, Logger log)
        throws ProvisioningException
    {
        if (Files.notExists(configOverlayPath))
        {
            throw new ProvisioningException(
                "Configured configOverlayPath '%s' does not exist."
                    .formatted(configOverlayPath)
            );
        }
        if (!Files.isDirectory(configOverlayPath))
        {
            throw new ProvisioningException(
                "Configured configOverlayPath '%s' is not a directory."
                    .formatted(configOverlayPath)
            );
        }

        final AtomicLong copiedFileCount = new AtomicLong(0L);
        log.log(Level.INFO, "LK_CONFIG: Applying configuration overlay from '%s' to '%s'."
            .formatted(configOverlayPath, targetServerDirectory));

        final Path overlayRoot = configOverlayPath.toAbsolutePath().normalize();
        final Path targetRoot = targetServerDirectory.toAbsolutePath().normalize();

        try
        {
            Files.walkFileTree(overlayRoot, new SimpleFileVisitor<>()
            {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException
                {
                    if (!directory.equals(overlayRoot) && Files.isSymbolicLink(directory))
                        throw new IOException("Symbolic links are not allowed in config overlays: " + directory);

                    final Path relative = overlayRoot.relativize(directory);
                    final Path targetDirectory = targetRoot.resolve(relative).normalize();
                    ensureInsideRootIo(targetDirectory, targetRoot, "config overlay directory");
                    Files.createDirectories(targetDirectory);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException
                {
                    if (Files.isSymbolicLink(file))
                        throw new IOException("Symbolic links are not allowed in config overlays: " + file);

                    final Path relative = overlayRoot.relativize(file);
                    final Path targetFile = targetRoot.resolve(relative).normalize();
                    ensureInsideRootIo(targetFile, targetRoot, "config overlay file");
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(
                        file,
                        targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                    );
                    copiedFileCount.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException exception)
        {
            throw new ProvisioningException(
                "Failed to apply config overlay from '%s' to '%s'."
                    .formatted(configOverlayPath, targetServerDirectory),
                exception
            );
        }

        log.log(Level.INFO, "LK_CONFIG: Applied overlay with %d file(s).".formatted(copiedFileCount.get()));
    }

    private static void installWorldFromFolder(WorldInputSpec world, Path targetWorldDirectory)
        throws ProvisioningException
    {
        if (!Files.isDirectory(world.sourcePath()))
        {
            throw new ProvisioningException(
                "World sourcePath '%s' is not a directory for world '%s'."
                    .formatted(world.sourcePath(), world.name())
            );
        }

        FileUtil.copyDirectoryRecursively(world.sourcePath(), targetWorldDirectory);
    }

    private static void installWorldFromArchive(WorldInputSpec world, Path targetWorldDirectory)
        throws ProvisioningException
    {
        final String normalizedName = world.sourcePath().getFileName() == null
            ? world.sourcePath().toString().toLowerCase(Locale.ROOT)
            : world.sourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
        if (!normalizedName.endsWith(".zip"))
        {
            throw new ProvisioningException(
                "World archive '%s' is not a .zip file for world '%s'."
                    .formatted(world.sourcePath(), world.name())
            );
        }

        if (!Files.isRegularFile(world.sourcePath()))
        {
            throw new ProvisioningException(
                "World archive sourcePath '%s' does not exist for world '%s'."
                    .formatted(world.sourcePath(), world.name())
            );
        }

        try
        {
            final Path extractionRoot = Files.createTempDirectory("lightkeeper-world-import-");
            try
            {
                extractZipArchive(world.sourcePath(), extractionRoot);
                final Path extractedWorldRoot = resolveExtractedWorldRoot(extractionRoot, world.sourcePath());
                FileUtil.copyDirectoryRecursively(extractedWorldRoot, targetWorldDirectory);
            }
            finally
            {
                FileUtil.deleteRecursively(extractionRoot, "world archive extraction directory");
            }
        }
        catch (IOException exception)
        {
            throw new ProvisioningException(
                "Failed to prepare temporary extraction directory for world archive '%s'."
                    .formatted(world.sourcePath()),
                exception
            );
        }
    }

    private static void extractZipArchive(Path archivePath, Path extractionRoot)
        throws ProvisioningException
    {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(archivePath)))
        {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null)
            {
                final String entryName = entry.getName();
                if (entryName == null || entryName.isBlank())
                    continue;

                final Path entryPath = extractionRoot.resolve(entryName).normalize();
                ensureInsideRoot(entryPath, extractionRoot, "zip entry");

                if (entry.isDirectory())
                {
                    Files.createDirectories(entryPath);
                }
                else
                {
                    if (entryPath.getParent() != null)
                        Files.createDirectories(entryPath.getParent());
                    try (OutputStream outputStream = Files.newOutputStream(entryPath))
                    {
                        zipInputStream.transferTo(outputStream);
                    }
                }
                zipInputStream.closeEntry();
            }
        }
        catch (IOException exception)
        {
            throw new ProvisioningException(
                "Failed to extract world archive '%s'."
                    .formatted(archivePath),
                exception
            );
        }
    }

    private static Path resolveExtractedWorldRoot(Path extractionRoot, Path archivePath)
        throws ProvisioningException
    {
        if (Files.isRegularFile(extractionRoot.resolve("level.dat")))
            return extractionRoot;

        final List<Path> topLevelEntries;
        try (var stream = Files.list(extractionRoot))
        {
            topLevelEntries = stream.toList();
        }
        catch (IOException exception)
        {
            throw new ProvisioningException(
                "Failed to inspect extracted world archive '%s'."
                    .formatted(archivePath),
                exception
            );
        }

        if (topLevelEntries.size() == 1 && Files.isDirectory(topLevelEntries.getFirst()))
            return topLevelEntries.getFirst();

        throw new ProvisioningException(
            "World archive '%s' is ambiguous. Expected exactly one world root directory."
                .formatted(archivePath)
        );
    }

    private static String validatePluginOutputFileName(String fileName)
        throws ProvisioningException
    {
        final String trimmed = fileName == null ? "" : fileName.trim();
        if (trimmed.isEmpty())
            throw new ProvisioningException("Plugin output filename may not be blank.");
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(".jar"))
            throw new ProvisioningException("Plugin output filename '%s' must end with .jar.".formatted(trimmed));
        if (trimmed.contains("/") || trimmed.contains("\\"))
        {
            throw new ProvisioningException(
                "Plugin output filename '%s' may not contain path separators."
                    .formatted(trimmed)
            );
        }
        return trimmed;
    }

    private static Path resolveChildPath(Path root, String childName, String context)
        throws ProvisioningException
    {
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path child = normalizedRoot.resolve(childName).normalize();
        ensureInsideRoot(child, normalizedRoot, context);
        return child;
    }

    private static void ensureInsideRoot(Path candidate, Path root, String context)
        throws ProvisioningException
    {
        if (!candidate.startsWith(root))
        {
            throw new ProvisioningException(
                "Resolved %s path '%s' escapes root '%s'."
                    .formatted(context, candidate, root)
            );
        }
    }

    private static void ensureInsideRootIo(Path candidate, Path root, String context)
        throws IOException
    {
        if (!candidate.startsWith(root))
        {
            throw new IOException(
                "Resolved %s path '%s' escapes root '%s'."
                    .formatted(context, candidate, root)
            );
        }
    }
}
