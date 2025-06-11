package nl.pim16aap2.lightkeeper.maven.util;

import org.apache.maven.plugin.MojoExecutionException;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
