package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.ModulePlanPaths;
import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;
import nl.pim16aap2.lightkeeper.runtime.TestModuleAnchor;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves a JUnit class's Maven-owned module plan or an authoritative explicit runtime manifest.
 */
public final class LightkeeperRuntimeResolver
{
    /** System property containing an authoritative explicit runtime manifest path. */
    public static final String RUNTIME_MANIFEST_PROPERTY = "lightkeeper.runtimeManifestPath";

    private LightkeeperRuntimeResolver()
    {
    }

    static ResolvedRuntime resolveRuntime(Class<?> testClass)
    {
        Objects.requireNonNull(testClass, "testClass may not be null.");
        final String explicitPath = System.getProperty(RUNTIME_MANIFEST_PROPERTY, "").trim();
        if (!explicitPath.isBlank())
        {
            return new ResolvedRuntime(validateExplicitPath(Path.of(explicitPath)), null);
        }

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

    static ResolvedRuntime resolveFromClassOutput(Path classOutputDirectory)
    {
        final String explicitPath = System.getProperty(RUNTIME_MANIFEST_PROPERTY, "").trim();
        if (!explicitPath.isBlank())
        {
            return new ResolvedRuntime(validateExplicitPath(Path.of(explicitPath)), null);
        }
        final Path output = classOutputDirectory.toAbsolutePath().normalize();
        try
        {
            final TestModuleAnchor anchor = GeneratedMetadata.read(
                ModulePlanPaths.classOutputAnchor(output), TestModuleAnchor.class
            );
            if (!Files.isSameFile(output, Path.of(anchor.classOutputDirectory())))
            {
                throw new IOException("Test-class ownership anchor belongs to another output directory.");
            }
            final ModuleTestPlan plan = GeneratedMetadata.read(
                ModulePlanPaths.modulePlan(Path.of(anchor.moduleDirectory())), ModuleTestPlan.class
            );
            if (!plan.moduleDirectory().equals(anchor.moduleDirectory()) ||
                !Files.isSameFile(output, Path.of(plan.classOutputDirectory())))
            {
                throw new IOException("Module plan does not belong to these test classes.");
            }
            return new ResolvedRuntime(null, plan);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Cannot resolve LightKeeper module for test output '" + output
                + "'. Run 'mvn verify' once with the lock-build and prepare-server goals bound, or set -D"
                + RUNTIME_MANIFEST_PROPERTY + "=<path> explicitly.", exception);
        }
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

    record ResolvedRuntime(@Nullable Path manifestPath, @Nullable ModuleTestPlan modulePlan)
    {
    }
}
