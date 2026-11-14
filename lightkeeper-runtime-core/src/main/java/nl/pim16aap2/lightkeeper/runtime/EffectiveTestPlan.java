package nl.pim16aap2.lightkeeper.runtime;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Effective variant inputs exported by the configured Maven mojo after its real lifecycle and artifact selection.
 * The POM remains authoritative on the next build. These inputs describe this preparation instead of guessed JAR names.
 *
 * @param moduleDirectory
 *     Canonical owning module.
 * @param executionId
 *     Bound Maven prepare-server execution.
 * @param serverType
 *     Effective server platform.
 * @param requestedServerVersion
 *     Effective requested version or supported alias.
 * @param target
 *     Logical artifact selection, independent of the last packaged filename.
 * @param configuration
 *     Non-secret effective LK settings; repository authentication must never be included.
 * @param assets
 *     Local inputs or materialized assets used by this preparation.
 */
public record EffectiveTestPlan(
    String moduleDirectory,
    String executionId,
    String serverType,
    String requestedServerVersion,
    Target target,
    Map<String, String> configuration,
    List<AssetInput> assets
)
{
    /** Validates variant identity and freezes its effective input collections. */
    public EffectiveTestPlan
    {
        moduleDirectory = ModulePlanPaths.absolutePath(moduleDirectory, "moduleDirectory");
        ModulePlanPaths.requireText(executionId, "executionId");
        ModulePlanPaths.requireText(serverType, "serverType");
        ModulePlanPaths.requireText(requestedServerVersion, "requestedServerVersion");
        Objects.requireNonNull(target, "target may not be null.");
        configuration = Collections.unmodifiableMap(new TreeMap<>(Map.copyOf(configuration)));
        assets = List.copyOf(assets);
    }

    /** Logical producer selection supported by the Maven target resolver. */
    public enum TargetKind
    {
        /** Current module's actual main or attached artifact. */
        CURRENT_MODULE,
        /** Declared dependency, preferring current reactor artifacts. */
        DEPENDENCY,
        /** Authoritative explicitly configured local artifact path. */
        PATH,
        /** Deliberate server-only test without a target plugin. */
        SERVER_ONLY,
        /** Existing explicit asset-only fixture without automatic plugin-under-test selection. */
        LEGACY_ASSETS
    }

    /**
     * Logical target selection, without an inferred output filename.
     *
     * @param kind
     *     Selection mode.
     * @param groupId
     *     Maven group, when selected.
     * @param artifactId
     *     Required Maven artifact ID for module/dependency selections.
     * @param classifier
     *     Optional attached-artifact classifier.
     * @param path
     *     Absolute path for explicit path selection.
     */
    public record Target(
        TargetKind kind,
        @Nullable String groupId,
        @Nullable String artifactId,
        @Nullable String classifier,
        @Nullable String path
    )
    {
        /** Validates the required inputs for the chosen selection mode. */
        public Target
        {
            Objects.requireNonNull(kind, "kind may not be null.");
            if (kind == TargetKind.CURRENT_MODULE || kind == TargetKind.DEPENDENCY)
            {
                ModulePlanPaths.requireText(Objects.requireNonNull(artifactId), "artifactId");
                if (path != null)
                {
                    throw new IllegalArgumentException("Maven target selection may not also specify a path.");
                }
            }
            else if (kind == TargetKind.PATH)
            {
                path = ModulePlanPaths.absolutePath(Objects.requireNonNull(path), "path");
                if (groupId != null || artifactId != null || classifier != null)
                {
                    throw new IllegalArgumentException(
                        "Explicit path selection may not also specify Maven coordinates."
                    );
                }
            }
            else if (groupId != null || artifactId != null || classifier != null || path != null)
            {
                throw new IllegalArgumentException(kind + " selection may not specify a target plugin.");
            }
        }
    }

    /**
     * One effective local/materialized asset input; remote URLs and credentials remain in Maven configuration.
     *
     * @param kind
     *     Asset domain: plugin, world, or overlay.
     * @param name
     *     Installed plugin/world/overlay name.
     * @param sourcePath
     *     Absolute local input path, or {@code null} for generated worlds.
     */
    public record AssetInput(String kind, String name, @Nullable String sourcePath)
    {
        /** Validates the input's domain, name and optional local path. */
        public AssetInput
        {
            ModulePlanPaths.requireText(kind, "asset kind");
            ModulePlanPaths.requireText(name, "asset name");
            if (sourcePath != null)
            {
                sourcePath = ModulePlanPaths.absolutePath(sourcePath, "sourcePath");
            }
        }
    }
}
