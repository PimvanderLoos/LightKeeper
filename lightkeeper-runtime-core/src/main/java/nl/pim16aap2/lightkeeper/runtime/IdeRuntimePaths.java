package nl.pim16aap2.lightkeeper.runtime;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Canonical path and privacy policy for module-owned generated LightKeeper state. */
public final class IdeRuntimePaths
{
    /** Name of the generated project-local state directory. */
    public static final String STATE_DIRECTORY_NAME = ".lightkeeper";

    private IdeRuntimePaths()
    {
    }

    /**
     * Returns the generated state directory owned by a Maven module.
     *
     * @param moduleDirectory
     *     Maven module directory.
     * @return The generated state directory.
     */
    public static Path stateDirectory(Path moduleDirectory)
    {
        return Objects.requireNonNull(moduleDirectory, "moduleDirectory may not be null.")
            .toAbsolutePath().normalize().resolve(STATE_DIRECTORY_NAME);
    }

    /**
     * Creates private generated state and prevents its runtime tokens/metadata from being added to Git accidentally.
     *
     * @param moduleDirectory
     *     Owning module or checkout.
     * @throws IOException
     *     If the directory or its initial ignore file cannot be created.
     */
    public static void prepareStateDirectory(Path moduleDirectory)
        throws IOException
    {
        final Path directory = stateDirectory(moduleDirectory);
        UserOnlyDirectory.prepareGenerated(directory);
        try
        {
            Files.writeString(directory.resolve(".gitignore"), "*\n", StandardOpenOption.CREATE_NEW);
        }
        catch (FileAlreadyExistsException ignored)
        {
            // Never overwrite an existing project-local ignore policy, including during concurrent initialization.
        }
    }
}
