package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.mojo.lockbuild.BuildGuardCoverage;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Checks effective LightKeeper setup without running build phases, resolving server downloads or changing files.
 * Configured JDK versions are read from local release metadata; wrappers with unknown versions get a warning.
 * Run {@code mvn lightkeeper:doctor} after adding the plugin. In a reactor, select the E2E module and upstreams with
 * {@code -pl :my-e2e -am}; this aggregate goal reports the selected preparation modules once.
 */
@Mojo(name = "doctor", aggregator = true, threadSafe = true)
public final class DoctorMojo extends AbstractMojo
{
    /** Effective current module, used when no selected module binds preparation yet. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private @Nullable MavenProject project;
    /** Current selected reactor and expression-evaluation context. */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;
    /** Mojo identity used by Maven's standard expression evaluator. */
    @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
    private @Nullable MojoExecution execution;
    /** Maven's own version information, without starting another process. */
    @Component
    private @Nullable RuntimeInformation runtimeInformation;

    /** Reports actionable observations and fails only for definite configuration errors. */
    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        final MavenSession current = Objects.requireNonNull(session, "Maven did not inject the session.");
        final List<MavenProject> preparations = current.getProjects().stream()
            .filter(candidate -> !BuildGuardCoverage.preparations(candidate).isEmpty())
            .toList();
        final List<MavenProject> targets = preparations.isEmpty()
            ? List.of(Objects.requireNonNull(project, "Maven did not inject the project.")) : preparations;
        final String version = Objects.requireNonNull(runtimeInformation, "Maven runtime information is missing.")
            .getMavenVersion();
        int errors = 0;
        for (final MavenProject target : targets)
        {
            getLog().info("LightKeeper doctor — " + target.getId());
            final MavenSession scoped = current.clone();
            scoped.setCurrentProject(target);
            final var evaluator = new PluginParameterExpressionEvaluator(scoped, Objects.requireNonNull(execution));
            final var inspection = new DoctorInspection(target, scoped, value -> evaluate(evaluator, value));
            for (final DoctorFinding finding : inspection.inspect(version))
            {
                getLog().info("[" + finding.level() + "] " + finding.subject() + ": " + finding.detail());
                if (!finding.action().isEmpty())
                {
                    getLog().info("  Next: " + finding.action());
                }
                if (finding.level() == DoctorFinding.Level.ERROR)
                {
                    ++errors;
                }
            }
        }
        getLog().info("Doctor completed without starting Minecraft or changing project files.");
        if (errors > 0)
        {
            throw new MojoFailureException("LightKeeper setup has " + errors
                + " error(s). Follow the actions above and rerun 'mvn lightkeeper:doctor'.");
        }
    }

    private static @Nullable Object evaluate(PluginParameterExpressionEvaluator evaluator, String value)
    {
        try
        {
            return evaluator.evaluate(value);
        }
        catch (ExpressionEvaluationException exception)
        {
            throw new IllegalArgumentException("Cannot evaluate a setup value. Check the referenced POM properties.",
                exception);
        }
    }
}
