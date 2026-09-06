package nl.pim16aap2.lightkeeper.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves a module-owned IDE runtime from a test class output directory.
 */
public final class IdeRuntimeDiscoveryResolver
{
    /** Maven build output directory name supported for automatic discovery. */
    public static final String MAVEN_OUTPUT_DIRECTORY_NAME = "target";

    private IdeRuntimeDiscoveryResolver()
    {
    }

    /**
     * Resolves the durable manifest belonging to the module that owns a class output directory.
     *
     * @param classOutputDirectory Test class code-source directory, normally {@code target/test-classes}.
     * @return The validated durable runtime manifest path.
     */
    public static Path resolve(Path classOutputDirectory)
    {
        final Path moduleDirectory = resolveModuleDirectory(classOutputDirectory);
        final Path discoveryPath = IdeRuntimePaths.discoveryFile(moduleDirectory);
        final IdeRuntimeDiscovery discovery;
        try
        {
            discovery = new IdeRuntimeDiscoveryReader().read(discoveryPath);
        }
        catch (IOException exception)
        {
            throw setupFailure(
                "IDE test setup is missing, corrupt, or incompatible at '%s': %s"
                    .formatted(discoveryPath, exception.getMessage()),
                exception
            );
        }

        final Path recordedModule = Path.of(discovery.moduleDirectory()).toAbsolutePath().normalize();
        if (!sameExistingDirectory(moduleDirectory, recordedModule))
        {
            throw setupFailure(
                "IDE runtime at '%s' belongs to module '%s', not test module '%s'."
                    .formatted(discoveryPath, recordedModule, moduleDirectory),
                null
            );
        }

        final Path manifestPath = Path.of(discovery.runtimeManifestPath()).toAbsolutePath().normalize();
        final Path expectedIdeDirectory = IdeRuntimePaths.ideDirectory(moduleDirectory);
        if (!manifestPath.startsWith(expectedIdeDirectory))
        {
            throw setupFailure(
                "IDE runtime manifest '%s' is outside its owning directory '%s'."
                    .formatted(manifestPath, expectedIdeDirectory),
                null
            );
        }
        if (!Files.isRegularFile(manifestPath))
            throw setupFailure("IDE runtime manifest '%s' does not exist.".formatted(manifestPath), null);
        return manifestPath;
    }

    /**
     * Resolves the owning Maven module from a supported class output directory.
     *
     * @param classOutputDirectory Class code-source directory.
     * @return The owning module directory.
     */
    public static Path resolveModuleDirectory(Path classOutputDirectory)
    {
        final Path outputDirectory = Objects.requireNonNull(
            classOutputDirectory,
            "classOutputDirectory may not be null."
        ).toAbsolutePath().normalize();
        final Path outputName = outputDirectory.getFileName();
        final Path buildDirectory = outputDirectory.getParent();
        if (outputName == null || buildDirectory == null || buildDirectory.getFileName() == null ||
            !(outputName.toString().equals("test-classes") || outputName.toString().equals("classes")) ||
            !buildDirectory.getFileName().toString().equals(MAVEN_OUTPUT_DIRECTORY_NAME) ||
            buildDirectory.getParent() == null)
        {
            throw new IllegalStateException(
                ("Unsupported test class output layout '%s'. Automatic IDE runtime discovery supports "
                    + "'<module>/target/test-classes'. Set -Dlightkeeper.runtimeManifestPath=<path> explicitly.")
                    .formatted(outputDirectory)
            );
        }
        return buildDirectory.getParent().toAbsolutePath().normalize();
    }

    private static boolean sameExistingDirectory(Path first, Path second)
    {
        try
        {
            return Files.isSameFile(first, second);
        }
        catch (IOException exception)
        {
            return first.equals(second);
        }
    }

    private static IllegalStateException setupFailure(String cause, Exception exception)
    {
        final String message = cause + " Run the configured setup execution with "
            + "'mvn lightkeeper:prepare-server@<execution-id> -Dlightkeeper.ide=true', or set "
            + "'-Dlightkeeper.runtimeManifestPath=<path>' explicitly.";
        return exception == null ? new IllegalStateException(message) : new IllegalStateException(message, exception);
    }
}
