package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PluginUnderTestResolverTest
{
    @TempDir
    Path directory;

    @Test
    void resolve_shouldUseReplacedMainArtifactRatherThanBuildFinalName()
        throws Exception
    {
        // setup
        final MavenProject project = project("example", "plugin");
        project.getBuild().setFinalName("misleading-original-name");
        final Path packaged = jar(project, "Assembly-Replaced.jar");

        // execute
        final var resolved = new PluginUnderTestResolver().resolve(project, List.of(project), null);

        // verify
        assertThat(resolved.sourceJar()).isEqualTo(packaged);
        assertThat(resolved.outputFileName()).isEqualTo("Assembly-Replaced.jar");
    }

    @Test
    void resolve_shouldPreferCurrentReactorArtifactOverInstalledDependency()
        throws Exception
    {
        // setup
        final MavenProject consumer = project("example", "e2e");
        final MavenProject producer = project("example", "packager");
        final Path current = jar(producer, "Current.jar");
        final Dependency dependency = dependency("example", "packager");
        consumer.setDependencies(List.of(dependency));
        final var stale = artifact("example", "packager", null);
        stale.setFile(Files.writeString(directory.resolve("stale.jar"), "old").toFile());
        consumer.setArtifacts(Set.of(stale));

        // execute
        final var resolved = new PluginUnderTestResolver().resolve(
            consumer,
            List.of(producer, consumer),
            new PluginUnderTestConfig(null, "packager", null, null)
        );

        // verify
        assertThat(resolved.sourceJar()).isEqualTo(current);
    }

    @Test
    void resolve_shouldSelectAttachedClassifierOfCurrentProject()
        throws Exception
    {
        // setup
        final MavenProject project = project("example", "plugin");
        jar(project, "main.jar");
        final var attached = artifact("example", "plugin", "shaded");
        final Path shaded = Files.writeString(directory.resolve("actual-shaded-output.jar"), "shaded");
        attached.setFile(shaded.toFile());
        project.addAttachedArtifact(attached);

        // execute
        final var resolved = new PluginUnderTestResolver().resolve(
            project,
            List.of(project),
            new PluginUnderTestConfig(null, null, "shaded", null)
        );

        // verify
        assertThat(resolved.sourceJar()).isEqualTo(shaded);
    }

    @Test
    void resolve_shouldResolveDeclaredExternalArtifact()
        throws Exception
    {
        // setup
        final MavenProject project = project("example", "e2e");
        final MavenProject producer = project("external", "plugin");
        final Path external = jar(producer, "external.jar");
        project.setDependencies(List.of(dependency("external", "plugin")));
        project.setArtifacts(Set.of(producer.getArtifact()));

        // execute
        final var resolved = new PluginUnderTestResolver().resolve(
            project,
            List.of(project),
            new PluginUnderTestConfig("external", "plugin", null, null)
        );

        // verify
        assertThat(resolved.sourceJar()).isEqualTo(external);
    }

    @Test
    void resolve_shouldRequireGroupForAmbiguousArtifactId()
        throws Exception
    {
        // setup
        final MavenProject project = project("example", "e2e");
        project.setDependencies(List.of(dependency("first", "plugin"), dependency("second", "plugin")));

        // execute
        final var result = assertThatThrownBy(() -> new PluginUnderTestResolver().resolve(
            project,
            List.of(project),
            new PluginUnderTestConfig(null, "plugin", null, null)
        ));

        // verify
        result.hasMessageContaining("matches 2").hasMessageContaining("pluginUnderTest.groupId");
    }

    @Test
    void resolve_shouldRejectMissingExplicitPathWithoutFallingBack()
        throws Exception
    {
        // setup
        final MavenProject project = project("example", "plugin");
        jar(project, "valid-main.jar");

        // execute
        final var result = assertThatThrownBy(() -> new PluginUnderTestResolver().resolve(
            project,
            List.of(project),
            new PluginUnderTestConfig(null, null, null, Path.of("missing.jar"))
        ));

        // verify
        result.hasMessageContaining("missing.jar").hasMessageContaining("not a readable JAR");
    }

    @Test
    void resolve_shouldRejectUnbuiltReactorArtifactWithoutUsingInstalledCopy()
        throws Exception
    {
        // setup
        final MavenProject project = project("example", "e2e");
        final MavenProject producer = project("example", "plugin");
        project.setDependencies(List.of(dependency("example", "plugin")));
        final var stale = artifact("example", "plugin", null);
        stale.setFile(Files.writeString(directory.resolve("installed.jar"), "old").toFile());
        project.setArtifacts(Set.of(stale));

        // execute
        final var result = assertThatThrownBy(() -> new PluginUnderTestResolver().resolve(
            project,
            List.of(producer, project),
            new PluginUnderTestConfig(null, "plugin", null, null)
        ));

        // verify
        result.hasMessageContaining("has no file").hasMessageContaining("package lifecycle");
    }

    @Test
    void resolve_shouldRejectConflictingPathAndArtifactSelector()
        throws Exception
    {
        // setup
        final MavenProject project = project("example", "plugin");

        // execute
        final var result = assertThatThrownBy(() -> new PluginUnderTestResolver().resolve(
            project,
            List.of(project),
            new PluginUnderTestConfig(null, "plugin", null, Path.of("explicit.jar"))
        ));

        // verify
        result.hasMessageContaining("cannot be combined");
    }

    private MavenProject project(String group, String name)
        throws Exception
    {
        final MavenProject project = new MavenProject();
        project.setGroupId(group);
        project.setArtifactId(name);
        project.setVersion("1.0");
        final Path module = Files.createDirectories(directory.resolve(group + "-" + name));
        project.setFile(module.resolve("pom.xml").toFile());
        project.setArtifact(artifact(group, name, null));
        return project;
    }

    private static DefaultArtifact artifact(String group, String name, @Nullable String classifier)
    {
        return new DefaultArtifact(group, name, "1.0", "test", "jar", classifier, new DefaultArtifactHandler("jar"));
    }

    private static Path jar(MavenProject project, String name)
        throws Exception
    {
        final Path jar = Files.writeString(project.getBasedir().toPath().resolve(name), "packaged");
        project.getArtifact().setFile(jar.toFile());
        return jar;
    }

    private static Dependency dependency(String group, String name)
    {
        final Dependency dependency = new Dependency();
        dependency.setGroupId(group);
        dependency.setArtifactId(name);
        dependency.setVersion("1.0");
        dependency.setScope("test");
        return dependency;
    }
}
