package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.mojo.lockbuild.BuildInvocationLeases;
import nl.pim16aap2.lightkeeper.provisioning.assets.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.provisioning.session.ServerSnapshots;
import nl.pim16aap2.lightkeeper.provisioning.util.FileUtil;
import nl.pim16aap2.lightkeeper.runtime.EffectiveTestPlan;
import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.MavenRuntimeContext;
import nl.pim16aap2.lightkeeper.runtime.ModulePlanPaths;
import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;
import nl.pim16aap2.lightkeeper.runtime.ProcessIdentity;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Captures real packaged inputs under Maven's early lease and exports their immutable runtime context. */
final class PrepareServerPlanExporter
{
    /** Normal Maven snapshots remain alive until every Failsafe fork in this invocation has finished. */
    private static final ConcurrentLinkedQueue<Path> OWNED_SNAPSHOTS = new ConcurrentLinkedQueue<>();
    /** Shutdown-only cleanup failures must not hide Maven's build result. */
    private static final System.Logger LOG = System.getLogger(PrepareServerPlanExporter.class.getName());

    static
    {
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("lightkeeper-snapshot-cleanup").unstarted(() ->
        {
            for (final Path directory : OWNED_SNAPSHOTS)
            {
                try
                {
                    FileUtil.deleteRecursively(directory, "completed Maven snapshot");
                }
                catch (RuntimeException exception)
                {
                    LOG.log(System.Logger.Level.WARNING, "Cannot remove completed snapshot '" + directory + "'.",
                        exception);
                }
            }
        }));
    }

    private PrepareServerPlanExporter()
    {
    }

    static boolean isBootstrap()
    {
        return System.getenv(MavenRuntimeContext.BOOTSTRAP_MODULE_ENV) != null;
    }

    static boolean isSelected(MavenProject project, String executionId)
        throws IOException
    {
        if (!isBootstrap())
        {
            return true;
        }
        final Path module = project.getBasedir().toPath().toRealPath();
        if (!module.toString().equals(System.getenv(MavenRuntimeContext.BOOTSTRAP_MODULE_ENV)))
        {
            return false;
        }
        final String requested = Objects.requireNonNull(System.getenv(MavenRuntimeContext.BOOTSTRAP_EXECUTION_ENV));
        final ModuleTestPlan plan = GeneratedMetadata.read(ModulePlanPaths.modulePlan(module), ModuleTestPlan.class);
        if (!requested.isEmpty())
        {
            if (!plan.executionIds().contains(requested))
            {
                throw new IllegalArgumentException("Unknown LightKeeper execution '" + requested
                    + "'. Available executions: " + plan.executionIds());
            }
            return executionId.equals(requested);
        }
        if (plan.defaultExecutionId() == null)
        {
            throw new IllegalArgumentException("Multiple active LightKeeper preparations: " + plan.executionIds()
                + ". Select one with -Dlightkeeper.executionId=<execution-id>.");
        }
        return executionId.equals(plan.defaultExecutionId());
    }

    static void write(
        @Nullable MavenProject project,
        @Nullable MavenSession session,
        @Nullable String executionId,
        EffectiveTestPlan.Target target,
        PrepareServerExecutionContext inputs,
        List<ResolvedPluginArtifact> plugins,
        @Nullable Path overlay,
        RuntimeManifest manifest
    )
        throws IOException
    {
        if (!BuildInvocationLeases.isHeld(session) || project == null)
        {
            if (isBootstrap())
            {
                throw new IllegalStateException("Automatic preparation requires an initialize-bound lock-build goal.");
            }
            return;
        }
        final Path module = project.getBasedir().toPath().toRealPath();
        final List<EffectiveTestPlan.AssetInput> assets = new ArrayList<>();
        plugins.forEach(plugin -> assets.add(new EffectiveTestPlan.AssetInput(
            "plugin", plugin.outputFileName(), plugin.sourceJar().toAbsolutePath().normalize().toString()
        )));
        inputs.worldInputSpecs().forEach(world -> assets.add(new EffectiveTestPlan.AssetInput(
            "world", world.name(), world.sourcePath().toAbsolutePath().normalize().toString()
        )));
        if (overlay != null)
        {
            assets.add(new EffectiveTestPlan.AssetInput("overlay", "config", overlay.toAbsolutePath().toString()));
        }
        final EffectiveTestPlan plan = new EffectiveTestPlan(
            module.toString(),
            Objects.requireNonNull(executionId, "Maven did not inject the prepare-server execution ID."),
            inputs.normalizedServerType(),
            inputs.serverVersion(),
            target,
            Map.of("memoryMb", Integer.toString(manifest.memoryMb()),
                "javaExecutablePath", Objects.requireNonNull(manifest.javaExecutablePath())),
            assets
        );
        final Path metadata = ServerSnapshots.capture(manifest, ModulePlanPaths.snapshotsDirectory(module));
        if (!isBootstrap())
        {
            OWNED_SNAPSHOTS.add(Objects.requireNonNull(metadata.getParent()));
        }
        final MavenRuntimeContext context = new MavenRuntimeContext(
            MavenRuntimeContext.SCHEMA_VERSION,
            plan,
            metadata.toString(),
            ProcessIdentity.current(),
            isBootstrap() ? Objects.requireNonNull(System.getenv(MavenRuntimeContext.BOOTSTRAP_NONCE_ENV)) :
                UUID.randomUUID().toString().replace("-", "")
        );
        GeneratedMetadata.write(context, ModulePlanPaths.runtimeContext(module, plan.executionId()));
        if (isBootstrap())
        {
            final Path result = Path.of(Objects.requireNonNull(System.getenv(MavenRuntimeContext.BOOTSTRAP_RESULT_ENV)))
                .toAbsolutePath().normalize();
            if (!result.startsWith(module.resolve(".lightkeeper/launches")) ||
                !result.getFileName().toString().equals("result.json"))
            {
                throw new IllegalArgumentException("Bootstrap result must belong to this module's launch directory.");
            }
            GeneratedMetadata.write(context, result);
        }
    }

    static EffectiveTestPlan.Target target(
        @Nullable MavenProject project,
        @Nullable PluginUnderTestConfig selection,
        boolean serverOnly,
        boolean legacyAssets
    )
    {
        if (serverOnly)
        {
            return new EffectiveTestPlan.Target(EffectiveTestPlan.TargetKind.SERVER_ONLY, null, null, null, null);
        }
        if (legacyAssets)
        {
            return new EffectiveTestPlan.Target(EffectiveTestPlan.TargetKind.LEGACY_ASSETS, null, null, null, null);
        }
        final MavenProject current = Objects.requireNonNull(project, "Plugin selection requires its Maven project.");
        if (selection != null && selection.path() != null)
        {
            return new EffectiveTestPlan.Target(
                EffectiveTestPlan.TargetKind.PATH, null, null, null,
                Objects.requireNonNull(selection.path()).toAbsolutePath().normalize().toString()
            );
        }
        final boolean dependency = selection != null && selection.artifactId() != null;
        return new EffectiveTestPlan.Target(
            dependency ? EffectiveTestPlan.TargetKind.DEPENDENCY : EffectiveTestPlan.TargetKind.CURRENT_MODULE,
            dependency ? Objects.requireNonNull(selection).groupId() : current.getGroupId(),
            dependency ? Objects.requireNonNull(selection).artifactId() : current.getArtifactId(),
            selection == null ? null : selection.classifier(),
            null
        );
    }
}
