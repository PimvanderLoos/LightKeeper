package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.runtime.BuildLeaseIdentity;
import nl.pim16aap2.lightkeeper.runtime.BuildOutputLease;
import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.MavenBuildContext;
import nl.pim16aap2.lightkeeper.runtime.MavenRuntimeContext;
import nl.pim16aap2.lightkeeper.runtime.ModulePlanPaths;
import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;
import nl.pim16aap2.lightkeeper.runtime.UserOnlyDirectory;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Re-evaluates and packages a native JUnit launch through the consumer's real Maven reactor. */
public final class NativeMavenPreparation
{
    /** Optional variant selection for modules with multiple active prepare-server executions. */
    public static final String EXECUTION_PROPERTY = "lightkeeper.executionId";
    /** One lifecycle message per actual producer build. */
    private static final System.Logger LOG = System.getLogger(NativeMavenPreparation.class.getName());

    private NativeMavenPreparation()
    {
    }

    /**
     * Uses a live ancestor Maven export or performs a fresh producer build for a direct JUnit launch.
     *
     * @param plan
     *     Maven-derived module ownership and retained build context.
     * @return Snapshot lifetime to retain in the JUnit root store, or close after standalone session creation.
     */
    public static PreparedTestRuntime prepare(ModuleTestPlan plan)
    {
        final String requested = System.getProperty(EXECUTION_PROPERTY, "").trim();
        final String provisional = requested.isEmpty() ? plan.defaultExecutionId() : requested;
        final Path module = Path.of(plan.moduleDirectory());
        try
        {
            if (provisional != null &&
                Files.isRegularFile(ModulePlanPaths.runtimeContext(module, provisional)))
            {
                final Path current = ModulePlanPaths.runtimeContext(module, provisional);
                final MavenRuntimeContext context = GeneratedMetadata.read(current, MavenRuntimeContext.class);
                validateOwner(context, plan, provisional);
                if (context.producer().matchesCurrentAncestry())
                {
                    validateOwner(context, plan, selectedExecution(plan));
                    return new PreparedTestRuntime(context, null);
                }
            }
            rejectUnpreparedAncestor(plan);
            return build(plan, requested);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Cannot prepare LightKeeper test module '" + module + "'.", exception);
        }
    }

    static String selectedExecution(ModuleTestPlan plan)
    {
        final String explicit = System.getProperty(EXECUTION_PROPERTY, "").trim();
        if (!explicit.isEmpty())
        {
            if (!plan.executionIds().contains(explicit))
            {
                throw new IllegalArgumentException("Unknown LightKeeper execution '" + explicit + "' in module '"
                    + plan.moduleDirectory() + "'. Available executions: " + plan.executionIds());
            }
            return explicit;
        }
        if (plan.defaultExecutionId() == null)
        {
            throw new IllegalStateException("Multiple LightKeeper executions in module '" + plan.moduleDirectory()
                + "': " + plan.executionIds() + ". Select one with -D" + EXECUTION_PROPERTY + "=<execution-id>.");
        }
        return plan.defaultExecutionId();
    }

    private static PreparedTestRuntime build(ModuleTestPlan plan, String execution)
        throws IOException
    {
        final Path module = Path.of(plan.moduleDirectory());
        final String nonce = UUID.randomUUID().toString().replace("-", "");
        final Path launch = module.resolve(".lightkeeper/launches").resolve(nonce);
        UserOnlyDirectory.prepare(launch);
        final Path result = launch.resolve("result.json");
        final Path log = launch.resolve("maven.log");
        final ProcessBuilder builder = new ProcessBuilder(command(plan));
        builder.directory(plan.reactorDirectory().toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        builder.environment().put("JAVA_HOME", plan.buildContext().javaHome());
        builder.environment().put("PATH", Path.of(plan.buildContext().javaHome(), "bin") + File.pathSeparator
            + Objects.requireNonNullElse(builder.environment().get("PATH"), ""));
        builder.environment().put(MavenRuntimeContext.BOOTSTRAP_MODULE_ENV, module.toString());
        builder.environment().put(MavenRuntimeContext.BOOTSTRAP_EXECUTION_ENV, execution);
        builder.environment().put(MavenRuntimeContext.BOOTSTRAP_RESULT_ENV, result.toString());
        builder.environment().put(MavenRuntimeContext.BOOTSTRAP_NONCE_ENV, nonce);
        final long timeout = timeoutSeconds(plan);
        LOG.log(System.Logger.Level.INFO, "LK_BUILD: Packaging current production inputs for ''{0}''; log: {1}",
            module, log);
        final Process child = builder.start();
        final Process reaper = OrphanReaperLauncher.launch(ProcessHandle.current().pid(), child.pid());
        final Thread shutdown = Thread.ofPlatform().name("lightkeeper-maven-cancellation")
            .unstarted(() -> terminate(child));
        Runtime.getRuntime().addShutdownHook(shutdown);
        try
        {
            child.getOutputStream().close();
            if (!child.waitFor(timeout, TimeUnit.SECONDS))
            {
                throw new IllegalStateException("LightKeeper Maven preparation timed out after " + timeout
                    + " seconds. See '" + log + "'.");
            }
            if (child.exitValue() != 0)
            {
                throw new IllegalStateException("LightKeeper Maven preparation failed with exit code "
                    + child.exitValue() + ". See '" + log + "'. No previous runtime will be used.");
            }
            final MavenRuntimeContext context = GeneratedMetadata.read(result, MavenRuntimeContext.class);
            validateOwner(context, plan, execution.isEmpty() ? context.plan().executionId() : execution);
            if (!nonce.equals(context.exportNonce()))
            {
                throw new IllegalStateException("Maven runtime export belongs to another JUnit launch: " + result);
            }
            return new PreparedTestRuntime(context, launch);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LightKeeper Maven preparation was cancelled. See '" + log + "'.",
                exception);
        }
        finally
        {
            terminate(child);
            Runtime.getRuntime().removeShutdownHook(shutdown);
            if (reaper != null)
            {
                reaper.destroy();
            }
        }
    }

    static List<String> command(ModuleTestPlan plan)
    {
        final MavenBuildContext context = plan.buildContext();
        final Path wrapper = plan.reactorDirectory().resolve("mvnw");
        final List<String> command = new ArrayList<>();
        command.add(Files.isExecutable(wrapper) ? wrapper.toString() : context.mavenExecutable());
        command.addAll(List.of("--batch-mode", "--no-transfer-progress", "-f", plan.reactorPom(), "-pl",
            Path.of(plan.moduleDirectory()).equals(plan.reactorDirectory()) ? "." :
                plan.reactorDirectory().relativize(Path.of(plan.moduleDirectory())).toString(), "-am"));
        final List<String> profiles = new ArrayList<>(context.activeProfiles());
        context.inactiveProfiles().forEach(profile -> profiles.add("!" + profile));
        if (!profiles.isEmpty())
        {
            command.add("-P" + String.join(",", profiles));
        }
        setting(command, "--settings", context.userSettings());
        setting(command, "--global-settings", context.globalSettings());
        setting(command, "--toolchains", context.userToolchains());
        setting(command, "--global-toolchains", context.globalToolchains());
        context.userProperties().forEach((key, value) -> command.add("-D" + key + "=" + value));
        command.addAll(List.of("-DskipTests=true", "-DskipITs=true", "-Dlightkeeper.skip=false",
            "-Dlightkeeper.ide=false", "pre-integration-test"));
        return List.copyOf(command);
    }

    private static void setting(List<String> command, String option, @Nullable String path)
    {
        if (path != null)
        {
            command.add(option);
            command.add(path);
        }
    }

    private static long timeoutSeconds(ModuleTestPlan plan)
    {
        final long seconds = Long.parseLong(plan.buildContext().userProperties()
            .getOrDefault("lightkeeper.buildTimeoutSeconds", "300"));
        if (seconds <= 0)
        {
            throw new IllegalArgumentException("lightkeeper.buildTimeoutSeconds must be positive.");
        }
        return seconds;
    }

    private static void validateOwner(MavenRuntimeContext context, ModuleTestPlan plan, String execution)
    {
        if (!context.plan().moduleDirectory().equals(plan.moduleDirectory()) ||
            !context.plan().executionId().equals(execution))
        {
            throw new IllegalStateException("Maven runtime export does not belong to the selected module/execution.");
        }
    }

    private static void rejectUnpreparedAncestor(ModuleTestPlan plan)
        throws IOException
    {
        final Path owner = BuildOutputLease.lockPath(Path.of(plan.moduleDirectory())).resolveSibling("owner.json");
        if (Files.isRegularFile(owner) &&
            GeneratedMetadata.read(owner, BuildLeaseIdentity.class).process().matchesCurrentAncestry())
        {
            throw new IllegalStateException("The outer Maven build has not captured packaged runtime '"
                + selectedExecution(plan) + "' for this module. "
                + "Run LightKeeper ITs with Failsafe using 'mvn verify', or launch JUnit directly after that setup.");
        }
    }

    private static void terminate(Process child)
    {
        if (child.isAlive())
        {
            child.descendants().forEach(ProcessHandle::destroyForcibly);
            child.destroyForcibly();
            try
            {
                child.waitFor(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
        }
    }
}
