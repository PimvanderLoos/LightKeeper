package nl.pim16aap2.lightkeeper.runtime;

import java.nio.file.Path;
import java.util.Objects;

/** Canonical module-owned locations for effective plans, class-output anchors and invocation exports. */
public final class ModulePlanPaths
{
    private ModulePlanPaths()
    {
    }

    /**
     * Returns the durable module build plan outside disposable Maven outputs.
     *
     * @param moduleDirectory
     *     Owning Maven module directory.
     * @return Module plan path.
     */
    public static Path modulePlan(Path moduleDirectory)
    {
        return IdeRuntimePaths.stateDirectory(moduleDirectory).resolve("test-plan/module.json");
    }

    /**
     * Returns the exact anchor written into Maven's effective test-class output directory.
     *
     * @param classOutputDirectory
     *     Effective Maven test output directory.
     * @return Anchor path, including a private child directory.
     */
    public static Path classOutputAnchor(Path classOutputDirectory)
    {
        return classOutputDirectory.toAbsolutePath().normalize()
            .resolve(IdeRuntimePaths.STATE_DIRECTORY_NAME)
            .resolve("module.json");
    }

    /**
     * Returns a variant's invocation context without treating the execution ID as a filesystem path.
     *
     * @param moduleDirectory
     *     Owning Maven module.
     * @param executionId
     *     Non-blank Maven prepare-server execution identifier.
     * @return Stable module-owned context path.
     */
    public static Path runtimeContext(Path moduleDirectory, String executionId)
    {
        requireText(executionId, "executionId");
        return IdeRuntimePaths.stateDirectory(moduleDirectory)
            .resolve("test-plan/invocations")
            .resolve(IdeRuntimeArtifactHasher.sha256(executionId) + ".json");
    }

    /**
     * Returns the module-owned immutable snapshot root shared by Maven exports and native JUnit preparation.
     *
     * @param moduleDirectory
     *     Owning Maven module.
     * @return Snapshot root directory.
     */
    public static Path snapshotsDirectory(Path moduleDirectory)
    {
        return IdeRuntimePaths.stateDirectory(moduleDirectory).resolve("snapshots");
    }

    static String absolutePath(String value, String name)
    {
        requireText(value, name);
        final Path path = Path.of(value);
        if (!path.isAbsolute())
        {
            throw new IllegalArgumentException(name + " must be absolute.");
        }
        return path.normalize().toString();
    }

    static void requireText(String value, String name)
    {
        if (Objects.requireNonNull(value, name + " may not be null.").isBlank())
        {
            throw new IllegalArgumentException(name + " may not be blank.");
        }
    }
}
