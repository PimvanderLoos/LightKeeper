package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.ModulePlanPaths;
import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;
import nl.pim16aap2.lightkeeper.runtime.TestModuleAnchor;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleTestPlanExporterTest
{
    @TempDir
    private Path root;

    @Test
    void write_shouldExportEffectiveOutputProfilesAndSafeProperties()
        throws Exception
    {
        // setup
        final MavenProject project = project("paper");
        final MavenSession session = session(project);

        // execute
        ModuleTestPlanExporter.write(project, session);

        // verify
        final ModuleTestPlan plan = GeneratedMetadata.read(ModulePlanPaths.modulePlan(root), ModuleTestPlan.class);
        assertThat(plan.executionIds()).containsExactly("paper");
        assertThat(plan.defaultExecutionId()).isEqualTo("paper");
        assertThat(plan.buildContext().activeProfiles()).containsExactly("integration");
        assertThat(plan.buildContext().inactiveProfiles()).containsExactly("release");
        assertThat(plan.buildContext().userProperties()).containsExactlyEntriesOf(java.util.Map.of("revision", "2"));
        assertThat(Files.readString(ModulePlanPaths.modulePlan(root))).doesNotContain("secret");
        final Path output = Path.of(project.getBuild().getTestOutputDirectory());
        final TestModuleAnchor anchor = GeneratedMetadata.read(
            ModulePlanPaths.classOutputAnchor(output), TestModuleAnchor.class
        );
        assertThat(anchor.classOutputDirectory()).isEqualTo(output.toString());
        assertThat(anchor.moduleDirectory()).isEqualTo(root.toRealPath().toString());
    }

    @Test
    void write_shouldRequireSelectionForMultipleActiveExecutions()
        throws Exception
    {
        // setup
        final MavenProject project = project("paper", "spigot", "disabled");
        project.getBuildPlugins().getFirst().getExecutions().getLast().setPhase("none");

        // execute
        ModuleTestPlanExporter.write(project, session(project));

        // verify
        final ModuleTestPlan plan = GeneratedMetadata.read(ModulePlanPaths.modulePlan(root), ModuleTestPlan.class);
        assertThat(plan.executionIds()).containsExactly("paper", "spigot");
        assertThat(plan.defaultExecutionId()).isNull();
    }

    @Test
    void write_shouldRejectAnUnguardedUpstreamProducer()
        throws Exception
    {
        // setup
        final MavenProject project = project("default");
        final MavenProject producer = new MavenProject();
        producer.setGroupId("example");
        producer.setArtifactId("producer");
        producer.setVersion("1");
        final ProjectDependencyGraph graph = mock();
        final MavenSession session = mock();
        when(session.getProjectDependencyGraph()).thenReturn(graph);
        when(graph.getUpstreamProjects(project, true)).thenReturn(List.of(producer));

        // execute
        final Throwable failure = catchThrowable(() -> ModuleTestPlanExporter.write(project, session));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("example:producer", "early", "reactor parent");
        assertThat(ModulePlanPaths.modulePlan(root)).doesNotExist();
    }

    @Test
    void write_shouldRejectAGuardBoundAfterProductionCompilation()
        throws Exception
    {
        // setup
        final MavenProject project = project("default");
        project.getBuildPlugins().getFirst().getExecutions().getFirst().setPhase("package");
        final MavenSession session = mock();

        // execute
        final Throwable failure = catchThrowable(() -> ModuleTestPlanExporter.write(project, session));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("early", "lock-build");
        verifyNoInteractions(session);
    }

    @Test
    void write_shouldIgnoreProducerModulesWithoutPreparation()
        throws Exception
    {
        // setup
        final MavenProject project = project();
        final MavenSession session = mock();

        // execute
        ModuleTestPlanExporter.write(project, session);

        // verify
        assertThat(ModulePlanPaths.modulePlan(root)).doesNotExist();
        verifyNoInteractions(session);
    }

    private MavenProject project(String... executions)
        throws Exception
    {
        final MavenProject project = new MavenProject();
        final Path pom = root.resolve("pom.xml");
        Files.writeString(pom, "<project/>");
        project.setFile(pom.toFile());
        final Build build = new Build();
        build.setTestOutputDirectory(root.resolve("custom/tests").toString());
        final Plugin plugin = new Plugin();
        plugin.setGroupId("nl.pim16aap2.lightkeeper");
        plugin.setArtifactId("lightkeeper-maven-plugin");
        for (final String id : executions)
        {
            final PluginExecution execution = new PluginExecution();
            execution.setId(id);
            execution.addGoal("lock-build");
            execution.addGoal("prepare-server");
            plugin.addExecution(execution);
        }
        build.addPlugin(plugin);
        project.getModel().setBuild(build);
        return project;
    }

    private MavenSession session(MavenProject project)
    {
        final var request = new DefaultMavenExecutionRequest();
        request.setPom(project.getFile());
        request.setActiveProfiles(List.of("integration"));
        request.setInactiveProfiles(List.of("release"));
        final Properties properties = new Properties();
        properties.setProperty("revision", "2");
        properties.setProperty("repository.password", "secret");
        final MavenSession session = mock();
        when(session.getRequest()).thenReturn(request);
        when(session.getUserProperties()).thenReturn(properties);
        return session;
    }
}
