package nl.pim16aap2.lightkeeper.runtime;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Canonical path policy for generated IDE test setup state.
 */
public final class IdeRuntimePaths
{
    /** Name of the generated project-local state directory. */
    public static final String STATE_DIRECTORY_NAME = ".lightkeeper";
    /** Name of the IDE test setup directory within the generated state directory. */
    public static final String IDE_DIRECTORY_NAME = "ide";
    /** Name of the discovery metadata document. */
    public static final String DISCOVERY_FILE_NAME = "discovery.json";
    /** Name of the provenance metadata document stored with a prepared runtime. */
    public static final String PROVENANCE_FILE_NAME = "provenance.json";
    /** Name of the operating-system lock used for IDE setup and launches. */
    public static final String LOCK_FILE_NAME = "runtime.lock";

    private IdeRuntimePaths()
    {
    }

    /**
     * Returns the generated state directory owned by a Maven module.
     *
     * @param moduleDirectory Maven module directory.
     * @return The generated state directory.
     */
    public static Path stateDirectory(Path moduleDirectory)
    {
        return normalizedModuleDirectory(moduleDirectory).resolve(STATE_DIRECTORY_NAME);
    }

    /**
     * Returns the IDE test setup directory owned by a Maven module.
     *
     * @param moduleDirectory Maven module directory.
     * @return The IDE test setup directory.
     */
    public static Path ideDirectory(Path moduleDirectory)
    {
        return stateDirectory(moduleDirectory).resolve(IDE_DIRECTORY_NAME);
    }

    /**
     * Returns the discovery metadata path for a Maven module.
     *
     * @param moduleDirectory Maven module directory.
     * @return The discovery metadata path.
     */
    public static Path discoveryFile(Path moduleDirectory)
    {
        return ideDirectory(moduleDirectory).resolve(DISCOVERY_FILE_NAME);
    }

    /**
     * Returns the root containing immutable IDE runtime preparations.
     *
     * @param moduleDirectory Maven module directory.
     * @return The preparations directory.
     */
    public static Path preparationsDirectory(Path moduleDirectory)
    {
        return ideDirectory(moduleDirectory).resolve("preparations");
    }

    /**
     * Returns the module-wide runtime lock path.
     *
     * @param moduleDirectory Maven module directory.
     * @return The lock file path.
     */
    public static Path lockFile(Path moduleDirectory)
    {
        return ideDirectory(moduleDirectory).resolve(LOCK_FILE_NAME);
    }

    private static Path normalizedModuleDirectory(Path moduleDirectory)
    {
        return Objects.requireNonNull(moduleDirectory, "moduleDirectory may not be null.")
            .toAbsolutePath()
            .normalize();
    }
}
