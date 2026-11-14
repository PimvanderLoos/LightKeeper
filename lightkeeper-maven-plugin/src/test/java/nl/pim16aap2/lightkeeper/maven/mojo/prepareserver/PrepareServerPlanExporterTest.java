package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.mojo.lockbuild.BuildInvocationLeases;
import nl.pim16aap2.lightkeeper.provisioning.assets.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.provisioning.session.ServerSnapshots;
import nl.pim16aap2.lightkeeper.runtime.EffectiveTestPlan.TargetKind;
import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.MavenRuntimeContext;
import nl.pim16aap2.lightkeeper.runtime.ModulePlanPaths;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrepareServerPlanExporterTest
{
    @TempDir
    private Path root;

    @Test
    void target_shouldUseLogicalCurrentArtifactAndClassifier()
    {
        // setup
        final var selection = new PluginUnderTestConfig(null, null, "all", null);

        // execute
        final var target = PrepareServerPlanExporter.target(project(), selection, false);

        // verify
        assertThat(target.kind()).isEqualTo(TargetKind.CURRENT_MODULE);
        assertThat(target.artifactId()).isEqualTo("plugin");
        assertThat(target.classifier()).isEqualTo("all");
        assertThat(target.path()).isNull();
    }

    @Test
    void target_shouldRetainDependencySelectionWithoutGuessingAnOutputFile()
    {
        // setup
        final var selection = new PluginUnderTestConfig("example", "packager", null, null);

        // execute
        final var target = PrepareServerPlanExporter.target(project(), selection, false);

        // verify
        assertThat(target.kind()).isEqualTo(TargetKind.DEPENDENCY);
        assertThat(target.groupId()).isEqualTo("example");
        assertThat(target.artifactId()).isEqualTo("packager");
        assertThat(target.path()).isNull();
    }

    @Test
    void target_shouldRetainAnAuthoritativeExplicitPath()
    {
        // setup
        final Path jar = root.resolve("selected.jar");
        final var selection = new PluginUnderTestConfig(null, null, null, jar);

        // execute
        final var target = PrepareServerPlanExporter.target(project(), selection, false);

        // verify
        assertThat(target.kind()).isEqualTo(TargetKind.PATH);
        assertThat(target.path()).isEqualTo(jar.toString());
    }

    @Test
    void target_shouldRepresentAnExplicitServerOnlyFixture()
    {
        // setup
        final MavenProject project = project();

        // execute
        final var target = PrepareServerPlanExporter.target(project, null, true);

        // verify
        assertThat(target.kind()).isEqualTo(TargetKind.SERVER_ONLY);
        assertThat(target.artifactId()).isNull();
    }

    @Test
    void write_shouldCaptureActualPackagedBytesAndLiveMavenIdentity()
        throws Exception
    {
        // setup
        final MavenProject project = project();
        final MavenSession session = mock();
        when(session.getExecutionRootDirectory()).thenReturn(root.toString());
        BuildInvocationLeases.acquire(session, Duration.ofSeconds(1), mock(Log.class));
        final Path server = Files.createDirectories(root.resolve("prepared"));
        final Path jar = Files.writeString(server.resolve("server.jar"), "server");
        final Path plugin = Files.writeString(server.resolve("plugin.jar"), "current-assembly-bytes");
        final RuntimeManifest manifest = new RuntimeManifest(
            "paper", "1.21.11", 132, "cache", server.toString(), jar.toString(), 1024,
            root.resolve("s/agent.sock").toString(), "token", null, null, RuntimeProtocol.VERSION,
            "agent", null, List.of(), Path.of(System.getProperty("java.home"), "bin/java").toString()
        );
        final var inputs = new PrepareServerExecutionContext(
            "paper", "latest-supported", root, root, root, root, root.resolve("manifest.json"),
            null, "fixture", List.of(), List.of()
        );

        // execute
        try
        {
            PrepareServerPlanExporter.write(
                project, session, "paper", null, false, inputs,
                List.of(new ResolvedPluginArtifact(plugin, "plugin.jar", "fixture")), null, manifest
            );
        }
        finally
        {
            BuildInvocationLeases.release(session);
        }
        Files.writeString(plugin, "later-build");

        // verify
        final MavenRuntimeContext context = GeneratedMetadata.read(
            ModulePlanPaths.runtimeContext(root, "paper"), MavenRuntimeContext.class
        );
        assertThat(context.producer().matchesCurrentAncestry()).isTrue();
        assertThat(context.plan().requestedServerVersion()).isEqualTo("latest-supported");
        final var snapshot = ServerSnapshots.read(Path.of(context.snapshotMetadataPath()));
        assertThat(Path.of(snapshot.manifest().serverDirectory()).resolve("plugin.jar"))
            .hasContent("current-assembly-bytes");
        assertThat(BuildInvocationLeases.isHeld(session)).isFalse();
    }

    private MavenProject project()
    {
        final MavenProject project = new MavenProject();
        project.setFile(root.resolve("pom.xml").toFile());
        project.setGroupId("example");
        project.setArtifactId("plugin");
        return project;
    }
}
