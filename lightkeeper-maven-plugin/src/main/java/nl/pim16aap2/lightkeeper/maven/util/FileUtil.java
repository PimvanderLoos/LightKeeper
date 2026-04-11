package nl.pim16aap2.lightkeeper.maven.util;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

public final class FileUtil
{
    private FileUtil()
    {
    }

    /**
     * Creates a directory if it does not already exist.
     *
     * @param path
     *     the directory to create.
     * @param name
     *     the name of the directory. This is used for logging purposes only.
     * @throws MojoExecutionException
     *     If the directory could not be created.
     */
    public static void createDirectories(Path path, String name)
        throws MojoExecutionException
    {
        if (Files.exists(path) && !Files.isDirectory(path))
        {
            throw new MojoExecutionException(
                "The path '" + path + "' exists but is not a directory."
            );
        }

        try
        {
            Files.createDirectories(path);
        }
        catch (Exception exception)
        {
            throw new MojoExecutionException(
                "Failed to create directory '" + name + "' at path '" + path + "'.",
                exception
            );
        }
    }

    /**
     * Cleans a directory by deleting all files and subdirectories within it.
     * <p>
     * Note that the directory itself is not deleted, only its contents.
     * <p>
     * If the directory does not exist, it will be created.
     *
     * @param path
     *     the path to delete.
     * @throws MojoExecutionException
     *     If the file or directory could not be deleted.
     */
    public static void cleanDirectory(Path path, String context)
        throws MojoExecutionException
    {
        if (Files.exists(path))
        {
            try
            {
                FileUtils.cleanDirectory(path.toFile());
            }
            catch (IOException exception)
            {
                throw new MojoExecutionException(
                    "Failed to clean %s with path '%s'.".formatted(context, path),
                    exception
                );
            }
        }
        else
        {
            createDirectories(path, context);
        }
    }

    /**
     * Deletes a file or directory recursively.
     *
     * @param path
     *     The file or directory to delete.
     * @param context
     *     Human-readable context for error messages.
     * @throws MojoExecutionException
     *     If deletion fails.
     */
    public static void deleteRecursively(Path path, String context)
        throws MojoExecutionException
    {
        if (Files.notExists(path))
            return;

        try (Stream<Path> stream = Files.walk(path))
        {
            stream.sorted(Comparator.reverseOrder())
                .forEach(current ->
                {
                    try
                    {
                        Files.deleteIfExists(current);
                    }
                    catch (IOException exception)
                    {
                        throw new UncheckedIOException(exception);
                    }
                });
        }
        catch (IOException | UncheckedIOException exception)
        {
            throw new MojoExecutionException(
                "Failed to delete %s with path '%s'.".formatted(context, path),
                exception
            );
        }
    }

    /**
     * Prunes expired sibling directories of the given current directory.
     * <p>
     * A sibling directory is pruned when it is a direct child of the same parent directory, is not the current
     * directory itself, and its age in days is greater than or equal to {@code expiryDays}.
     * <p>
     * Deletion failures are collected and returned in the result without failing the operation.
     *
     * @param currentDirectory
     *     The directory to keep while pruning sibling directories from its parent.
     * @param expiryDays
     *     Age threshold in days. Directories with age greater than or equal to this value are eligible.
     * @return The result containing deleted and failed directory paths.
     * @throws MojoExecutionException
     *     If sibling directories cannot be enumerated.
     */
    public static PruneResult pruneSiblingDirectoriesOlderThan(Path currentDirectory, int expiryDays)
        throws MojoExecutionException
    {
        final Path normalizedCurrent = currentDirectory.toAbsolutePath().normalize();
        final Path parent = normalizedCurrent.getParent();
        if (parent == null || Files.notExists(parent))
            return new PruneResult(List.of(), List.of());

        final List<Path> deleted = new ArrayList<>();
        final List<Path> failed = new ArrayList<>();
        try (Stream<Path> siblings = Files.list(parent))
        {
            siblings
                .filter(Files::isDirectory)
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> !path.equals(normalizedCurrent))
                .forEach(path ->
                {
                    final long age;
                    try
                    {
                        age = getFileAgeInDays(path);
                    }
                    catch (RuntimeException exception)
                    {
                        failed.add(path);
                        return;
                    }

                    if (age < expiryDays)
                        return;

                    try
                    {
                        deleteRecursively(path, "unused cache directory");
                        deleted.add(path);
                    }
                    catch (MojoExecutionException exception)
                    {
                        failed.add(path);
                    }
                });
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to enumerate sibling directories under '%s'.".formatted(parent),
                exception
            );
        }
        return new PruneResult(List.copyOf(deleted), List.copyOf(failed));
    }

    /**
     * Gets the age of a file in days based on its last modified time.
     *
     * @param file
     *     the file to check.
     * @return the age of the file in days.
     *
     * @throws RuntimeException
     *     If the last modified time could not be retrieved.
     */
    public static long getFileAgeInDays(Path file)
    {
        try
        {
            final Instant fileInstant = Files.getLastModifiedTime(file).toInstant();
            final Instant now = Instant.now();
            return Duration.between(fileInstant, now).toDays();
        }
        catch (IOException exception)
        {
            throw new RuntimeException("Failed to get last modified time for file: " + file, exception);
        }
    }

    /**
     * Result of pruning sibling directories.
     *
     * @param deletedDirectories
     *     Sibling directories that were successfully deleted.
     * @param failedDirectories
     *     Sibling directories that could not be deleted or inspected.
     */
    public record PruneResult(
        List<Path> deletedDirectories,
        List<Path> failedDirectories
    )
    {
    }

    /**
     * Recursively copies all files and subdirectories from source to target.
     *
     * @param source
     *     The source directory.
     * @param target
     *     The target directory.
     * @throws MojoExecutionException
     *     If copying fails.
     */
    public static void copyDirectoryRecursively(Path source, Path target)
        throws MojoExecutionException
    {
        if (!Files.isDirectory(source))
            throw new MojoExecutionException("Source path '%s' is not a directory.".formatted(source));

        try
        {
            Files.createDirectories(target);
            Files.walkFileTree(source, new SimpleFileVisitor<>()
            {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException
                {
                    final Path relative = source.relativize(dir);
                    Files.createDirectories(target.resolve(relative));
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException
                {
                    final Path relative = source.relativize(file);
                    Files.copy(
                        file,
                        target.resolve(relative),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                    );
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to copy source directory '%s' to '%s'.".formatted(source, target),
                exception
            );
        }
    }
}
