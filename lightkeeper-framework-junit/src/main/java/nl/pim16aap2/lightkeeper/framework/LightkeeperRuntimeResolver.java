package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscoveryResolver;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves a LightKeeper runtime manifest for a JUnit test class.
 */
public final class LightkeeperRuntimeResolver
{
    /** System property containing an authoritative explicit runtime manifest path. */
    public static final String RUNTIME_MANIFEST_PROPERTY = "lightkeeper.runtimeManifestPath";

    private LightkeeperRuntimeResolver()
    {
    }

    /**
     * Resolves the explicit runtime manifest override or the IDE setup owned by the test class's Maven module.
     *
     * @param testClass Test class used to locate the owning module.
     * @return The selected runtime manifest path.
     */
    public static Path resolve(Class<?> testClass)
    {
        Objects.requireNonNull(testClass, "testClass may not be null.");
        final String explicitPath = System.getProperty(RUNTIME_MANIFEST_PROPERTY, "").trim();
        if (!explicitPath.isBlank())
            return validateExplicitPath(Path.of(explicitPath));

        final Path classOutputDirectory;
        try
        {
            classOutputDirectory = Path.of(
                Objects.requireNonNull(
                    testClass.getProtectionDomain().getCodeSource(),
                    "Test class code source is unavailable."
                ).getLocation().toURI()
            );
        }
        catch (URISyntaxException | IllegalArgumentException exception)
        {
            throw new IllegalStateException(
                "Cannot locate test class '%s'. Set -D%s=<path> explicitly."
                    .formatted(testClass.getName(), RUNTIME_MANIFEST_PROPERTY),
                exception
            );
        }
        return resolveFromClassOutput(classOutputDirectory);
    }

    /**
     * Resolves IDE setup from an explicit test class output directory.
     *
     * @param classOutputDirectory Maven test class output directory.
     * @return The selected runtime manifest path.
     */
    public static Path resolveFromClassOutput(Path classOutputDirectory)
    {
        final String explicitPath = System.getProperty(RUNTIME_MANIFEST_PROPERTY, "").trim();
        if (!explicitPath.isBlank())
            return validateExplicitPath(Path.of(explicitPath));
        return IdeRuntimeDiscoveryResolver.resolve(classOutputDirectory);
    }

    private static Path validateExplicitPath(Path path)
    {
        final Path manifestPath = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(manifestPath))
        {
            throw new IllegalStateException(
                "Explicit runtime manifest from -D%s does not exist at '%s'."
                    .formatted(RUNTIME_MANIFEST_PROPERTY, manifestPath)
            );
        }
        return manifestPath;
    }
}
