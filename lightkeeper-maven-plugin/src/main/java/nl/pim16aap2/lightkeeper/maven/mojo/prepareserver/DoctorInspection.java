package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.mojo.lockbuild.BuildGuardCoverage;
import nl.pim16aap2.lightkeeper.provisioning.assets.PluginDescriptorValidator;
import nl.pim16aap2.lightkeeper.provisioning.assets.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.ModulePlanPaths;
import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import nl.pim16aap2.lightkeeper.runtime.TestModuleAnchor;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;

import static nl.pim16aap2.lightkeeper.maven.mojo.prepareserver.DoctorFinding.Level.*;

/** Read-only inspection of effective Maven configuration; no downloads, build execution or generated state. */
final class DoctorInspection
{
    /** Feature version required by the currently supported Minecraft server runtime. */
    private static final int MINIMUM_JAVA_FEATURE = 21;
    /** Both modern release values and legacy 1.8 release values can be diagnosed without executing them. */
    private static final Pattern JAVA_FEATURE = Pattern.compile("^(?:1\\.)?(\\d+)");
    /** Effective selected test module. */
    private final MavenProject project;
    /** Current invocation supplies selected upstreams and user-property precedence. */
    private final MavenSession session;
    /** Standard Maven expression evaluator scoped to this module. */
    private final Function<String, @Nullable Object> evaluate;
    /** Findings in human-readable setup order. */
    private final List<DoctorFinding> findings = new ArrayList<>();

    DoctorInspection(MavenProject project, MavenSession session, Function<String, @Nullable Object> evaluate)
    {
        this.project = project;
        this.session = session;
        this.evaluate = evaluate;
    }

    List<DoctorFinding> inspect(String mavenVersion)
    {
        add(new ComparableVersion(mavenVersion).compareTo(new ComparableVersion("3.9")) >= 0 ? OK : ERROR,
            "Maven", mavenVersion, "Use Maven 3.9 or newer.");
        add(OK, "Build Java", Runtime.version().toString(), "");
        final boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        add(windows ? WARN : OK, "Transport", "Unix-domain sockets; native Windows execution is not verified.",
            windows ? "Use a verified Linux environment or validate Windows transport before relying on it." : "");

        final List<PluginExecution> preparations = BuildGuardCoverage.preparations(project);
        if (preparations.isEmpty())
        {
            add(ERROR, "Preparation", "No active prepare-server execution is bound in this module.",
                "Bind lock-build and prepare-server, or select the E2E module with -pl :artifact-id -am.");
            return List.copyOf(findings);
        }
        add(OK, "Preparation", preparations.stream().map(PluginExecution::getId).toList().toString(), "");
        final List<MavenProject> missing = BuildGuardCoverage.missing(project, session);
        add(missing.isEmpty() ? OK : ERROR, "Producer guards",
            missing.isEmpty() ? "Selected producers have early lock-build executions."
                : "Missing early guard: " + missing.stream().map(MavenProject::getId).toList(),
            missing.isEmpty() ? "" : "Inherit <goals><goal>lock-build</goal></goals> in the reactor parent's "
                + "LightKeeper plugin execution, before compilation or packaging.");
        inspectFailsafe();
        for (final PluginExecution preparation : preparations)
        {
            inspectVariant(preparation);
        }
        if (preparations.size() > 1)
        {
            add(WARN, "Variants", "Several preparations are active.",
                "Select the intended profile or execution for a direct JUnit launch; see the IDE guide.");
        }
        inspectMetadata();
        return List.copyOf(findings);
    }

    private void inspectFailsafe()
    {
        final Plugin failsafe = project.getPlugin(PrepareServerSkipPolicy.FAILSAFE_KEY);
        final List<String> goals = failsafe == null ? List.of() : failsafe.getExecutions().stream()
            .filter(execution -> !"none".equals(execution.getPhase()))
            .flatMap(execution -> execution.getGoals().stream())
            .toList();
        if (!goals.containsAll(List.of("integration-test", "verify")))
        {
            add(ERROR, "Failsafe", "The integration-test and verify goals are not both bound.",
                "Bind both Failsafe goals in this test module and set failIfNoTests=true.");
            return;
        }
        try
        {
            final boolean skipped = property("skipITs") || property("maven.test.skip") ||
                PrepareServerSkipPolicy.shouldSkip(project, evaluate, property("skipTests"));
            add(skipped ? WARN : OK, "Failsafe",
                skipped ? "Integration tests are currently skipped." : "IT goals are bound.",
                skipped ? "Remove the test-skip setting before expecting preparation or IT execution." : "");
        }
        catch (IllegalArgumentException exception)
        {
            add(ERROR, "Failsafe", message(exception), "Correct the referenced test-skip property.");
        }
    }

    private void inspectVariant(PluginExecution execution)
    {
        final Plugin plugin = Objects.requireNonNull(project.getPlugin(BuildGuardCoverage.PLUGIN_KEY));
        final Xpp3Dom configuration = Xpp3Dom.mergeXpp3Dom(
            copy(execution.getConfiguration()),
            copy(plugin.getConfiguration())
        );
        try
        {
            final String type = value(configuration, "serverType", "lightkeeper.serverType",
                PrepareServerConfigurationValidator.SERVER_TYPE_PAPER).toLowerCase(Locale.ROOT);
            if (!PrepareServerConfigurationValidator.SUPPORTED_SERVER_TYPES.contains(type))
            {
                add(ERROR, "Server " + execution.getId(), "Unsupported server type.", "Choose paper or spigot.");
                return;
            }
            final String version = value(configuration, "serverVersion", "lightkeeper.serverVersion",
                PrepareServerConfigurationValidator.DEFAULT_SERVER_VERSION);
            final boolean supported = version.equals(PrepareServerConfigurationValidator.DEFAULT_SERVER_VERSION) ||
                version.equals(RuntimeProtocol.SUPPORTED_MINECRAFT_VERSION);
            add(supported ? OK : WARN, "Server " + execution.getId(), type + "; requested version " + version
                + "; this agent supports " + RuntimeProtocol.SUPPORTED_MINECRAFT_VERSION,
                supported ? "" : "Use latest-supported unless deliberately investigating a different runtime; "
                    + "the LightKeeper infrastructure must remain compatible.");
            final String java = value(configuration, "javaExecutablePath", "lightkeeper.javaExecutablePath",
                "${java.home}/bin/java");
            inspectJava(java);
            inspectTarget(configuration);
        }
        catch (IllegalArgumentException | MojoExecutionException exception)
        {
            add(ERROR, "Configuration " + execution.getId(), message(exception),
                "Correct the selector or referenced Maven properties, then rerun doctor.");
        }
    }

    private void inspectTarget(Xpp3Dom configuration)
        throws MojoExecutionException
    {
        if (Boolean.parseBoolean(value(configuration, "serverOnly", "lightkeeper.serverOnly", "false")))
        {
            add(OK, "Target", "Explicit server-only mode; no target plugin is required.", "");
            return;
        }
        final Xpp3Dom target = configuration.getChild("pluginUnderTest");
        final Xpp3Dom plugins = configuration.getChild("plugins");
        if (target == null && plugins != null && plugins.getChildren().length > 0)
        {
            add(WARN, "Target", "Legacy asset-only plugin list; no plugin-under-test selection is inferred.",
                "Keep this intentional setup, or add pluginUnderTest to select a target alongside the extra assets.");
            return;
        }
        final String path = optional(target, "path");
        final var selection = new PluginUnderTestConfig(
            optional(target, "groupId"), optional(target, "artifactId"), optional(target, "classifier"),
            path == null ? null : Path.of(path)
        );
        final var resolver = new PluginUnderTestResolver();
        final var dependency = resolver.selectedDependency(project, selection);
        add(OK, "Target selection", selection.path() != null ? "Explicit local JAR path."
            : (dependency == null ? "This module's actual packaged artifact." : "Declared dependency "
                + dependency.getGroupId() + ":" + dependency.getArtifactId()), "");
        final ResolvedPluginArtifact artifact;
        try
        {
            artifact = resolver.resolve(project, session.getProjects(), selection);
        }
        catch (MojoExecutionException exception)
        {
            if (selection.path() != null)
            {
                add(ERROR, "Packaged JAR", message(exception),
                    "Fix pluginUnderTest.path to name an existing readable packaged plugin JAR.");
            }
            else
            {
                add(WARN, "Packaged JAR", "This doctor-only invocation does not expose the packaged artifact; "
                    + "no build or dependency resolution was requested.",
                    "Run normal Maven verify to validate the actual packaging. This warning alone does not "
                        + "mean an existing JAR is stale or invalid.");
            }
            return;
        }
        try
        {
            PluginDescriptorValidator.validate(artifact, project.getId());
            add(OK, "Packaged JAR", artifact.sourceJar().getFileName() + " has valid archive and descriptor.", "");
        }
        catch (RuntimeException exception)
        {
            add(ERROR, "Packaged JAR", message(exception),
                "Fix the JAR or its plugin.yml/paper-plugin.yml; enabled state is checked only by your tests.");
        }
    }

    private void inspectMetadata()
    {
        final Path module = project.getBasedir().toPath().toAbsolutePath().normalize();
        final Path output = Path.of(project.getBuild().getTestOutputDirectory()).toAbsolutePath().normalize();
        try
        {
            final ModuleTestPlan plan = GeneratedMetadata.read(
                ModulePlanPaths.modulePlan(module), ModuleTestPlan.class
            );
            final TestModuleAnchor anchor = GeneratedMetadata.read(
                ModulePlanPaths.classOutputAnchor(output), TestModuleAnchor.class
            );
            if (!module.toString().equals(plan.moduleDirectory()) ||
                !module.toString().equals(anchor.moduleDirectory()) ||
                !output.toString().equals(plan.classOutputDirectory()) ||
                !output.toString().equals(anchor.classOutputDirectory()))
            {
                throw new IOException("Generated metadata belongs to another module or test output.");
            }
            add(OK, "Direct JUnit", "Ownership metadata is ready; saved production inputs will be rebuilt.", "");
        }
        catch (IOException exception)
        {
            add(WARN, "Direct JUnit", "Initial module metadata is absent or needs regeneration.",
                "Run mvn verify once after setup, cleaning/moving the checkout, or changing test-output location.");
        }
    }

    private boolean property(String name)
    {
        return Boolean.parseBoolean(session.getUserProperties().getProperty(name, "false"));
    }

    private @Nullable String optional(@Nullable Xpp3Dom parent, String name)
    {
        if (parent == null || parent.getChild(name) == null || parent.getChild(name).getValue() == null)
        {
            return null;
        }
        final Object resolved = evaluate.apply(parent.getChild(name).getValue());
        if (resolved == null || resolved.toString().isBlank() || resolved.toString().contains("${"))
        {
            throw new IllegalArgumentException("Cannot resolve configured " + name + ".");
        }
        return resolved.toString().trim();
    }

    private String value(Xpp3Dom configuration, String key, String property, String fallback)
    {
        final String configured = optional(configuration, key);
        if (configured != null)
        {
            return configured;
        }
        final Object resolved = evaluate.apply(session.getUserProperties().getProperty(property, fallback));
        if (resolved == null || resolved.toString().contains("${"))
        {
            throw new IllegalArgumentException("Cannot resolve " + key + ".");
        }
        return resolved.toString().trim();
    }

    private static Xpp3Dom copy(@Nullable Object value)
    {
        return value instanceof Xpp3Dom configuration ? new Xpp3Dom(configuration) : new Xpp3Dom("configuration");
    }

    private void inspectJava(String executable)
    {
        final Path selected = executable(executable);
        if (selected == null)
        {
            add(ERROR, "Server Java", "Executable not found: " + executable,
                "Set javaExecutablePath to an existing Java executable, or restore its default.");
            return;
        }
        try
        {
            final Path bin = selected.toRealPath().getParent();
            final Path home = bin == null ? null : bin.getParent();
            if (home == null)
            {
                throw new IOException("Java home cannot be determined.");
            }
            final byte[] bytes;
            try (var input = Files.newInputStream(home.resolve("release")))
            {
                bytes = input.readNBytes(65_537);
            }
            if (bytes.length > 65_536)
            {
                throw new IOException("JDK release metadata is too large.");
            }
            final Properties release = new Properties();
            release.load(new StringReader(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
            final String version = release.getProperty("JAVA_VERSION", "").replace("\"", "");
            final var feature = JAVA_FEATURE.matcher(version);
            if (!feature.find())
            {
                throw new IOException("JDK feature version cannot be determined.");
            }
            final boolean supported = Integer.parseInt(feature.group(1)) >= MINIMUM_JAVA_FEATURE;
            add(supported ? OK : ERROR, "Server Java", selected + "; version " + version,
                supported ? "" : "Use Java " + MINIMUM_JAVA_FEATURE + "+ for this server, then rerun doctor.");
        }
        catch (IOException | IllegalArgumentException exception)
        {
            add(WARN, "Server Java", "Executable found, but its Java version cannot be verified: " + selected,
                "Check that this executable or wrapper runs Java " + MINIMUM_JAVA_FEATURE + "+. Doctor never runs it.");
        }
    }

    private @Nullable Path executable(String value)
    {
        final Path path = Path.of(value);
        if (path.isAbsolute() || path.getNameCount() > 1)
        {
            final Path selected = path.isAbsolute() ? path : project.getBasedir().toPath().resolve(path);
            return Files.isRegularFile(selected) && Files.isExecutable(selected) ? selected : null;
        }
        final String searchPath = Objects.requireNonNullElse(System.getenv("PATH"), "");
        for (final String directory : searchPath.split(File.pathSeparator, -1))
        {
            final Path selected = Path.of(directory).resolve(path);
            if (!directory.isEmpty() && Files.isRegularFile(selected) && Files.isExecutable(selected))
            {
                return selected;
            }
        }
        return null;
    }

    private static String message(Exception exception)
    {
        return Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getSimpleName());
    }

    private void add(DoctorFinding.Level level, String subject, String detail, String action)
    {
        findings.add(new DoctorFinding(level, subject, detail, level == OK ? "" : action));
    }
}
