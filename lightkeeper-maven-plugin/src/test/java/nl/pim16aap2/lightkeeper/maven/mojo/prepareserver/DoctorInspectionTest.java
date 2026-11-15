package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorInspectionTest
{
    @TempDir
    private Path root;

    @Test
    void inspect_shouldAcceptInitialSetupWithoutWritingOrRequiringPackagedOutputs()
        throws Exception
    {
        // setup
        final MavenProject project = project();
        final MavenSession session = session();
        final List<Path> before = files();

        // execute
        final var findings = inspect(project, session);

        // verify
        assertThat(findings).noneMatch(finding -> finding.level() == DoctorFinding.Level.ERROR);
        assertThat(findings).anyMatch(finding -> finding.subject().equals("Packaged JAR") &&
            finding.level() == DoctorFinding.Level.WARN && finding.action().contains("verify"));
        assertThat(findings).anyMatch(finding -> finding.subject().equals("Direct JUnit") &&
            finding.level() == DoctorFinding.Level.WARN);
        assertThat(files()).containsExactlyElementsOf(before);
    }

    @Test
    void inspect_shouldReportSeveralIndependentSetupErrorsTogether()
        throws Exception
    {
        // setup
        final MavenProject project = project();
        project.getBuildPlugins().getFirst().getExecutions().getFirst().getGoals().remove("lock-build");
        project.getBuild().setPlugins(List.of(project.getBuildPlugins().getFirst()));
        project.getBuildPlugins().getFirst().setConfiguration(configuration("serverType", "unknown"));
        final MavenSession session = mock();

        // execute
        final var findings = inspect(project, session);

        // verify
        assertThat(findings).filteredOn(finding -> finding.level() == DoctorFinding.Level.ERROR)
            .extracting(DoctorFinding::subject).containsExactly("Producer guards", "Failsafe", "Server default");
        assertThat(findings).filteredOn(finding -> finding.level() == DoctorFinding.Level.ERROR)
            .allSatisfy(finding -> assertThat(finding.action()).isNotBlank());
    }

    @Test
    void inspect_shouldUseExecutionConfigurationOverPluginAndUserDefaults()
        throws Exception
    {
        // setup
        final MavenProject project = project();
        final Plugin plugin = project.getBuildPlugins().getFirst();
        plugin.setConfiguration(configuration("serverType", "invalid"));
        plugin.getExecutions().getFirst().setConfiguration(configuration("serverType", "spigot"));
        final MavenSession session = session();
        session.getUserProperties().setProperty("lightkeeper.serverType", "invalid-too");

        // execute
        final var findings = inspect(project, session);

        // verify
        assertThat(findings).filteredOn(finding -> finding.subject().equals("Server default"))
            .singleElement().satisfies(finding -> assertThat(finding.detail()).startsWith("spigot;"));
        assertThat(((Xpp3Dom) plugin.getConfiguration()).getChild("serverType").getValue()).isEqualTo("invalid");
    }

    @Test
    void inspect_shouldRejectUndeclaredProducerWithoutResolvingIt()
        throws Exception
    {
        // setup
        final MavenProject project = project();
        final Xpp3Dom config = new Xpp3Dom("configuration");
        final Xpp3Dom target = new Xpp3Dom("pluginUnderTest");
        final Xpp3Dom artifact = new Xpp3Dom("artifactId");
        artifact.setValue("missing-producer");
        target.addChild(artifact);
        config.addChild(target);
        project.getBuildPlugins().getFirst().setConfiguration(config);
        final MavenSession session = session();

        // execute
        final var findings = inspect(project, session);

        // verify
        assertThat(findings).anySatisfy(finding ->
        {
            assertThat(finding.level()).isEqualTo(DoctorFinding.Level.ERROR);
            assertThat(finding.detail()).contains("matches 0 declared dependencies");
            assertThat(finding.action()).contains("selector");
        });
    }

    @Test
    void inspect_shouldValidateExplicitJarWithoutLoadingItsMainClass()
        throws Exception
    {
        // setup
        final MavenProject project = project();
        final Path jar = root.resolve("target-plugin.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar)))
        {
            output.putNextEntry(new JarEntry("plugin.yml"));
            output.write("name: Example\nversion: 1\nmain: intentionally.unavailable.Main\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        final Xpp3Dom config = new Xpp3Dom("configuration");
        final Xpp3Dom target = new Xpp3Dom("pluginUnderTest");
        final Xpp3Dom path = new Xpp3Dom("path");
        path.setValue(jar.getFileName().toString());
        target.addChild(path);
        config.addChild(target);
        project.getBuildPlugins().getFirst().setConfiguration(config);
        final MavenSession session = session();

        // execute
        final var findings = inspect(project, session);

        // verify
        assertThat(findings).noneMatch(finding -> finding.level() == DoctorFinding.Level.ERROR);
        assertThat(findings).anyMatch(finding -> finding.subject().equals("Packaged JAR") &&
            finding.level() == DoctorFinding.Level.OK);
    }

    @Test
    void inspect_shouldDistinguishCurrentFailsafeSkipTestsFromSkipITs()
        throws Exception
    {
        // setup
        final MavenProject project = project();
        final MavenSession session = session();
        session.getUserProperties().setProperty("skipTests", "true");

        // execute
        final var unitSkip = inspect(project, session);
        session.getUserProperties().setProperty("skipITs", "true");
        final var integrationSkip = inspect(project, session);

        // verify
        assertThat(unitSkip).anyMatch(finding -> finding.subject().equals("Failsafe") &&
            finding.level() == DoctorFinding.Level.OK);
        assertThat(integrationSkip).anyMatch(finding -> finding.subject().equals("Failsafe") &&
            finding.level() == DoctorFinding.Level.WARN && finding.action().contains("skip"));
    }

    @Test
    void inspect_shouldRejectConfiguredOldServerJavaWithoutExecutingIt()
        throws Exception
    {
        // setup
        final MavenProject project = project();
        final Path home = Files.createDirectory(root.resolve("java17"));
        final Path bin = Files.createDirectory(home.resolve("bin"));
        final Path executable = Files.writeString(bin.resolve("java"), "#!/bin/sh\nexit 99\n");
        Files.setPosixFilePermissions(executable,
            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"17.0.10\"\n");
        project.getBuildPlugins().getFirst().setConfiguration(
            configuration("javaExecutablePath", executable.toString()));
        final MavenSession session = session();

        // execute
        final var findings = inspect(project, session);

        // verify
        assertThat(findings).filteredOn(finding -> finding.subject().equals("Server Java"))
            .singleElement().satisfies(finding ->
            {
                assertThat(finding.level()).isEqualTo(DoctorFinding.Level.ERROR);
                assertThat(finding.detail()).contains("17.0.10");
                assertThat(finding.action()).contains("Java 21+");
            });
    }

    @Test
    void inspect_shouldWarnForAnUnknownWrapperVersionWithoutExecutingIt()
        throws Exception
    {
        // setup
        final MavenProject project = project();
        final Path executable = Files.writeString(root.resolve("java-wrapper"), "#!/bin/sh\nexit 99\n");
        Files.setPosixFilePermissions(executable,
            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        project.getBuildPlugins().getFirst().setConfiguration(
            configuration("javaExecutablePath", executable.toString()));
        final MavenSession session = session();

        // execute
        final var findings = inspect(project, session);

        // verify
        assertThat(findings).filteredOn(finding -> finding.subject().equals("Server Java"))
            .singleElement().satisfies(finding ->
            {
                assertThat(finding.level()).isEqualTo(DoctorFinding.Level.WARN);
                assertThat(finding.action()).contains("never runs it");
            });
    }

    private List<DoctorFinding> inspect(MavenProject project, MavenSession session)
    {
        return new DoctorInspection(project, session,
            value -> value.replace("${java.home}", System.getProperty("java.home"))).inspect("3.9.9");
    }

    private List<Path> files()
        throws Exception
    {
        try (var paths = Files.walk(root))
        {
            return paths.sorted().toList();
        }
    }

    private MavenProject project()
        throws Exception
    {
        final MavenProject project = new MavenProject();
        project.setGroupId("example");
        project.setArtifactId("plugin");
        project.setVersion("1");
        project.setFile(Files.writeString(root.resolve("pom.xml"), "<project/>").toFile());
        final Build build = new Build();
        build.setTestOutputDirectory(root.resolve("target/test-classes").toString());
        final Plugin plugin = new Plugin();
        plugin.setGroupId("nl.pim16aap2.lightkeeper");
        plugin.setArtifactId("lightkeeper-maven-plugin");
        final PluginExecution prepare = new PluginExecution();
        prepare.addGoal("lock-build");
        prepare.addGoal("prepare-server");
        plugin.addExecution(prepare);
        build.addPlugin(plugin);
        final Plugin failsafe = new Plugin();
        failsafe.setGroupId("org.apache.maven.plugins");
        failsafe.setArtifactId("maven-failsafe-plugin");
        failsafe.setVersion("3.6.1");
        final PluginExecution tests = new PluginExecution();
        tests.addGoal("integration-test");
        tests.addGoal("verify");
        failsafe.addExecution(tests);
        build.addPlugin(failsafe);
        project.getModel().setBuild(build);
        return project;
    }

    private MavenSession session()
    {
        final MavenSession session = mock();
        when(session.getUserProperties()).thenReturn(new Properties());
        return session;
    }

    private Xpp3Dom configuration(String name, String value)
    {
        final Xpp3Dom configuration = new Xpp3Dom("configuration");
        final Xpp3Dom child = new Xpp3Dom(name);
        child.setValue(value);
        configuration.addChild(child);
        return configuration;
    }
}
