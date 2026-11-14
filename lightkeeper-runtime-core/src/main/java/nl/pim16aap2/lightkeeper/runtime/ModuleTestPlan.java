package nl.pim16aap2.lightkeeper.runtime;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Durable Maven-derived ownership and build scope for one test module.
 * Actual packaged artifacts are exported later through {@link MavenRuntimeContext}, after the real lifecycle.
 *
 * @param schemaVersion
 *     Module plan format version.
 * @param moduleDirectory
 *     Canonical owning test module directory.
 * @param modulePom
 *     Actual Maven project file for that module.
 * @param reactorPom
 *     Original reactor entry project file used for producer builds.
 * @param classOutputDirectory
 *     Effective Maven test-class output directory, including custom layouts.
 * @param executionIds
 *     Bound prepare-server executions available in this module.
 * @param defaultExecutionId
 *     Unambiguous or explicitly prepared default execution, when available.
 * @param buildContext
 *     Supported Maven invocation context for re-evaluation.
 */
public record ModuleTestPlan(
    int schemaVersion,
    String moduleDirectory,
    String modulePom,
    String reactorPom,
    String classOutputDirectory,
    List<String> executionIds,
    @Nullable String defaultExecutionId,
    MavenBuildContext buildContext
)
{
    /** Supported module plan format. */
    public static final int SCHEMA_VERSION = 1;

    /** Validates ownership, known variant IDs and the module plan format. */
    public ModuleTestPlan
    {
        if (schemaVersion != SCHEMA_VERSION)
        {
            throw new IllegalArgumentException("Unsupported module test plan schema: " + schemaVersion);
        }
        moduleDirectory = ModulePlanPaths.absolutePath(moduleDirectory, "moduleDirectory");
        modulePom = ModulePlanPaths.absolutePath(modulePom, "modulePom");
        reactorPom = ModulePlanPaths.absolutePath(reactorPom, "reactorPom");
        classOutputDirectory = ModulePlanPaths.absolutePath(classOutputDirectory, "classOutputDirectory");
        final Path pomParent = Path.of(modulePom).getParent();
        if (pomParent == null || !pomParent.equals(Path.of(moduleDirectory)))
        {
            throw new IllegalArgumentException("The module POM must belong to the owning module directory.");
        }
        executionIds = List.copyOf(executionIds);
        executionIds.forEach(id -> ModulePlanPaths.requireText(id, "executionId"));
        if (executionIds.isEmpty() || new HashSet<>(executionIds).size() != executionIds.size())
        {
            throw new IllegalArgumentException("A module plan requires distinct prepare-server execution IDs.");
        }
        if (defaultExecutionId != null && !executionIds.contains(defaultExecutionId))
        {
            throw new IllegalArgumentException("Default execution must belong to the module plan.");
        }
        Objects.requireNonNull(buildContext, "buildContext may not be null.");
    }

    /**
     * Returns the reactor directory used for a checkout-wide build lease.
     *
     * @return Parent directory of the retained reactor POM.
     */
    public Path reactorDirectory()
    {
        return Objects.requireNonNull(Path.of(reactorPom).getParent(), "Reactor POM must have a parent directory.");
    }
}
