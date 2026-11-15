package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimePaths;
import nl.pim16aap2.lightkeeper.runtime.MavenBuildContext;
import nl.pim16aap2.lightkeeper.runtime.ModulePlanPaths;
import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;
import nl.pim16aap2.lightkeeper.runtime.TestModuleAnchor;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
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
    private ModuleTestPlanExporter()
    {
    }

    static void write(MavenProject project, MavenSession session)
        throws IOException
    {
        final List<String> executions = BuildGuardCoverage.preparations(project).stream()
            .map(execution -> execution.getId())
            .distinct()
            .toList();
        if (executions.isEmpty())
        {
            return;
        }
        BuildGuardCoverage.require(project, session);

        final Path module = project.getBasedir().toPath().toRealPath();
        IdeRuntimePaths.prepareStateDirectory(module);
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
