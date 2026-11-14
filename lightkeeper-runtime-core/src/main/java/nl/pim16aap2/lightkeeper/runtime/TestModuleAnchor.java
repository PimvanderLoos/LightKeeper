package nl.pim16aap2.lightkeeper.runtime;

/**
 * Ownership anchor written at the exact test-class output location, without a global classpath search.
 *
 * @param schemaVersion
 *     Anchor format version.
 * @param moduleDirectory
 *     Canonical owning Maven module.
 * @param classOutputDirectory
 *     Exact Maven test-class output location that contains this anchor.
 */
public record TestModuleAnchor(int schemaVersion, String moduleDirectory, String classOutputDirectory)
{
    /** Supported anchor format. */
    public static final int SCHEMA_VERSION = 1;

    /** Validates the anchor format and absolute ownership paths. */
    public TestModuleAnchor
    {
        if (schemaVersion != SCHEMA_VERSION)
        {
            throw new IllegalArgumentException("Unsupported test module anchor schema: " + schemaVersion);
        }
        moduleDirectory = ModulePlanPaths.absolutePath(moduleDirectory, "moduleDirectory");
        classOutputDirectory = ModulePlanPaths.absolutePath(classOutputDirectory, "classOutputDirectory");
    }
}
