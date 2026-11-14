package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PrepareServerSkipPolicyTest
{
    @ParameterizedTest
    @CsvSource({"3.5.6,true", "3.6.0,false", "3.6.1,false"})
    void shouldSkip_shouldRespectFailsafeVersionForSkipTestsProperty(String version, boolean expected)
    {
        // setup
        final MavenProject project = project(version, configuration("configuration", "ignored"));

        // execute
        final boolean result = PrepareServerSkipPolicy.shouldSkip(project, expression -> expression, true);

        // verify
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"skip", "skipITs", "skipTests"})
    void shouldSkip_shouldHonorExplicitFailsafeSkipConfiguration(String key)
    {
        // setup
        final MavenProject project = project("3.6.1", configuration(key, "true"));

        // execute
        final boolean result = PrepareServerSkipPolicy.shouldSkip(project, expression -> expression, false);

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void shouldSkip_shouldEvaluateConfiguredSkipExpression()
    {
        // setup
        final MavenProject project = project("3.6.1", configuration("skipITs", "${consumer.skipRuntime}"));

        // execute
        final boolean result = PrepareServerSkipPolicy.shouldSkip(
            project,
            expression -> expression.equals("${consumer.skipRuntime}") ? "true" : expression,
            false
        );

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void shouldSkip_shouldKeepPreparationWhenAnyExecutionIsActive()
    {
        // setup
        final MavenProject project = project("3.6.1", configuration("skipITs", "true"));
        final Plugin plugin = project.getPlugin(PrepareServerSkipPolicy.FAILSAFE_KEY);
        final PluginExecution active = new PluginExecution();
        active.setId("active-server");
        active.setGoals(List.of("integration-test"));
        active.setConfiguration(configuration("skipITs", "false"));
        plugin.addExecution(active);

        // execute
        final boolean result = PrepareServerSkipPolicy.shouldSkip(project, expression -> expression, false);

        // verify
        assertThat(result).isFalse();
    }

    @Test
    void shouldSkip_shouldRejectUnresolvedSkipExpression()
    {
        // setup
        final MavenProject project = project("3.6.1", configuration("skipITs", "${missing.property}"));

        // execute
        final var result = assertThatThrownBy(() -> PrepareServerSkipPolicy.shouldSkip(project, value -> value, false));

        // verify
        result.hasMessageContaining("Cannot evaluate Failsafe").hasMessageContaining("missing.property");
    }

    @Test
    void shouldSkip_shouldAllowPreparationWithoutFailsafe()
    {
        // setup
        final MavenProject project = new MavenProject();

        // execute
        final boolean result = PrepareServerSkipPolicy.shouldSkip(project, expression -> expression, false);

        // verify
        assertThat(result).isFalse();
    }

    private static MavenProject project(String version, Xpp3Dom configuration)
    {
        final MavenProject project = new MavenProject();
        final Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-failsafe-plugin");
        plugin.setVersion(version);
        plugin.setConfiguration(configuration);
        final PluginExecution execution = new PluginExecution();
        execution.setId("run-integration-tests");
        execution.setGoals(List.of("integration-test", "verify"));
        plugin.addExecution(execution);
        project.getBuild().addPlugin(plugin);
        return project;
    }

    private static Xpp3Dom configuration(String name, String value)
    {
        final Xpp3Dom configuration = new Xpp3Dom("configuration");
        final Xpp3Dom child = new Xpp3Dom(name);
        child.setValue(value);
        configuration.addChild(child);
        return configuration;
    }
}
