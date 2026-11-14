package nl.pim16aap2.lightkeeper.runtime;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * Supported non-secret Maven invocation settings retained for a later native JUnit producer build.
 * Repository credentials remain in Maven settings or the environment; arbitrary original CLI flags are not replayed.
 *
 * @param mavenExecutable
 *     Maven executable used when the project has no wrapper.
 * @param javaHome
 *     Java installation used for Maven, independently of the selected Minecraft executable.
 * @param activeProfiles
 *     Explicitly requested active profile IDs, in request order.
 * @param inactiveProfiles
 *     Explicitly disabled profile IDs.
 * @param userProperties
 *     Retained version, compiler and LightKeeper input properties from the documented allowlist.
 * @param userSettings
 *     Existing user settings file path, when selected.
 * @param globalSettings
 *     Existing global settings file path, when selected.
 * @param userToolchains
 *     Existing user toolchains file path, when selected.
 * @param globalToolchains
 *     Existing global toolchains file path, when selected.
 */
public record MavenBuildContext(
    String mavenExecutable,
    String javaHome,
    List<String> activeProfiles,
    List<String> inactiveProfiles,
    Map<String, String> userProperties,
    @Nullable String userSettings,
    @Nullable String globalSettings,
    @Nullable String userToolchains,
    @Nullable String globalToolchains
)
{
    /** Fixed retention contract; no arbitrary repository authentication properties can enter the generated plan. */
    public static final Set<String> RETAINED_PROPERTIES = Set.of(
        "revision", "changelist", "sha1", "maven.repo.local", "maven.javadoc.skip",
        "maven.compiler.release", "maven.compiler.source", "maven.compiler.target", "maven.compiler.encoding",
        "maven.compiler.parameters", "maven.compiler.enablePreview", "project.build.sourceEncoding",
        "lightkeeper.serverOnly", "lightkeeper.serverType", "lightkeeper.serverVersion",
        "lightkeeper.jarCacheDirectoryRoot", "lightkeeper.baseServerCacheDirectoryRoot",
        "lightkeeper.pluginArtifactCacheDirectoryRoot", "lightkeeper.serverWorkDirectoryRoot",
        "lightkeeper.runtimeManifestPath", "lightkeeper.agentSocketDirectory", "lightkeeper.versionedCacheDirectories",
        "lightkeeper.jarCacheExpiryDays", "lightkeeper.baseServerCacheExpiryDays",
        "lightkeeper.cleanupUnusedCacheDirectories", "lightkeeper.serverInitTimeoutSeconds",
        "lightkeeper.serverStopTimeoutSeconds", "lightkeeper.serverStartMaxAttempts", "lightkeeper.memoryMb",
        "lightkeeper.javaExecutablePath", "lightkeeper.extraJvmArgs", "lightkeeper.userAgent",
        "lightkeeper.configOverlayPath", "lightkeeper.buildTimeoutSeconds"
    );

    /** Validates retained properties and freezes caller-owned collections. */
    public MavenBuildContext
    {
        ModulePlanPaths.requireText(mavenExecutable, "mavenExecutable");
        javaHome = ModulePlanPaths.absolutePath(javaHome, "javaHome");
        activeProfiles = List.copyOf(activeProfiles);
        inactiveProfiles = List.copyOf(inactiveProfiles);
        activeProfiles.forEach(profile -> ModulePlanPaths.requireText(profile, "active profile"));
        inactiveProfiles.forEach(profile -> ModulePlanPaths.requireText(profile, "inactive profile"));
        if (!RETAINED_PROPERTIES.containsAll(userProperties.keySet()))
        {
            throw new IllegalArgumentException("Generated Maven context contains unsupported retained properties.");
        }
        userProperties = Collections.unmodifiableMap(new TreeMap<>(Map.copyOf(userProperties)));
        userSettings = optionalPath(userSettings, "userSettings");
        globalSettings = optionalPath(globalSettings, "globalSettings");
        userToolchains = optionalPath(userToolchains, "userToolchains");
        globalToolchains = optionalPath(globalToolchains, "globalToolchains");
    }

    /**
     * Selects the supported property subset without copying repository credentials or test/invocation controls.
     *
     * @param properties
     *     Original Maven user properties.
     * @return Immutable supported property subset.
     */
    public static Map<String, String> retainProperties(Properties properties)
    {
        final Map<String, String> retained = new TreeMap<>();
        properties.stringPropertyNames().stream()
            .filter(RETAINED_PROPERTIES::contains)
            .forEach(name -> retained.put(name, properties.getProperty(name)));
        return Collections.unmodifiableMap(retained);
    }

    private static @Nullable String optionalPath(@Nullable String value, String name)
    {
        return value == null ? null : ModulePlanPaths.absolutePath(value, name);
    }
}
