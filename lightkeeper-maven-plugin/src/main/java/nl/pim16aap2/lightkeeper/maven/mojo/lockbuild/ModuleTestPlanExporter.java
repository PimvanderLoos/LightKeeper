package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.MavenBuildContext;
import nl.pim16aap2.lightkeeper.runtime.ModulePlanPaths;
import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;
import nl.pim16aap2.lightkeeper.runtime.TestModuleAnchor;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Writes Maven's effective test-module ownership and supported producer-build context at initialize. */
final class ModuleTestPlanExporter
{
    /** The canonical Maven plugin whose bound preparation executions define test variants. */
    private static final String PLUGIN_KEY = "nl.pim16aap2.lightkeeper:lightkeeper-maven-plugin";

    private ModuleTestPlanExporter()
    {
    }

    static void write(MavenProject project, MavenSession session)
        throws IOException
    {
        final Plugin plugin = project.getPlugin(PLUGIN_KEY);
        if (plugin == null)
        {
            return;
        }
        final List<String> executions = plugin.getExecutions().stream()
            .filter(execution -> !"none".equals(execution.getPhase()))
            .filter(execution -> execution.getGoals().contains("prepare-server"))
            .map(execution -> execution.getId())
            .distinct()
            .toList();
        if (executions.isEmpty())
        {
            return;
        }
        validateProducerGuards(project, session);

        final Path module = project.getBasedir().toPath().toRealPath();
        final Path output = Path.of(project.getBuild().getTestOutputDirectory()).toAbsolutePath().normalize();
        final MavenExecutionRequest request = session.getRequest();
        final File reactorPom = Objects.requireNonNull(request.getPom(), "Maven request has no reactor POM.");
        final ModuleTestPlan plan = new ModuleTestPlan(
            ModuleTestPlan.SCHEMA_VERSION,
            module.toString(),
            project.getFile().toPath().toRealPath().toString(),
            reactorPom.toPath().toRealPath().toString(),
            output.toString(),
            executions,
            executions.size() == 1 ? executions.getFirst() : null,
            context(session)
        );
        GeneratedMetadata.write(plan, ModulePlanPaths.modulePlan(module));
        GeneratedMetadata.write(
            new TestModuleAnchor(TestModuleAnchor.SCHEMA_VERSION, module.toString(), output.toString()),
            ModulePlanPaths.classOutputAnchor(output)
        );
    }

    private static void validateProducerGuards(MavenProject project, MavenSession session)
    {
        final ProjectDependencyGraph graph = session.getProjectDependencyGraph();
        if (graph == null)
        {
            return;
        }
        for (final MavenProject upstream : graph.getUpstreamProjects(project, true))
        {
            if ("pom".equals(upstream.getPackaging()))
            {
                continue;
            }
            final Plugin plugin = upstream.getPlugin(PLUGIN_KEY);
            if (plugin == null || plugin.getExecutions().stream().noneMatch(execution ->
                execution.getGoals().contains("lock-build") && (execution.getPhase() == null ||
                    "initialize".equals(execution.getPhase()) || "validate".equals(execution.getPhase()))))
            {
                throw new IllegalStateException("Producer module '" + upstream.getId()
                    + "' has no early LightKeeper lock-build execution. Inherit the lightweight lock-build goal "
                    + "from the reactor parent so packaging and snapshot capture cannot overlap another build.");
            }
        }
    }

    private static MavenBuildContext context(MavenSession session)
    {
        final MavenExecutionRequest request = session.getRequest();
        final String mavenHome = System.getProperty("maven.home");
        final String executable = mavenHome == null ? "mvn" : Path.of(mavenHome, "bin", "mvn").toString();
        return new MavenBuildContext(
            executable,
            System.getProperty("java.home"),
            request.getActiveProfiles(),
            request.getInactiveProfiles(),
            MavenBuildContext.retainProperties(session.getUserProperties()),
            existing(request.getUserSettingsFile()),
            existing(request.getGlobalSettingsFile()),
            existing(request.getUserToolchainsFile()),
            existing(request.getGlobalToolchainsFile())
        );
    }

    private static @Nullable String existing(@Nullable File file)
    {
        return file != null && Files.isRegularFile(file.toPath()) ? file.toPath().toAbsolutePath().toString() : null;
    }
}
