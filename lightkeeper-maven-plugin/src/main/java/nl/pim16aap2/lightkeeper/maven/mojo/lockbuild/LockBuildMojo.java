package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/**
 * Protects selected module outputs for the owning Maven CLI invocation.
 * Bind beside {@code prepare-server} in a plugin module. In a reactor, inherit this lightweight goal from the
 * reactor root; keep full server preparation bound only in test modules.
 */
@Mojo(name = "lock-build", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public final class LockBuildMojo extends AbstractMojo
{
    /** Current Maven invocation shared across the selected reactor modules. */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;

    /** Effective module whose production outputs are about to be built. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private @Nullable MavenProject project;

    /** Maximum wait for another cooperating build or native JUnit preparation to release producer outputs. */
    @Parameter(property = "lightkeeper.buildTimeoutSeconds", defaultValue = "300")
    private long buildTimeoutSeconds;

    /** Acquires the invocation lease before compilation, generation, filtering and packaging change any outputs. */
    @Override
    public void execute()
        throws MojoExecutionException
    {
        try
        {
            if (buildTimeoutSeconds <= 0)
            {
                throw new MojoExecutionException("lightkeeper.buildTimeoutSeconds must be positive.");
            }
            final MavenSession current = Objects.requireNonNull(session, "Maven did not inject the build session.");
            BuildInvocationLeases.acquire(
                current,
                Duration.ofSeconds(buildTimeoutSeconds),
                getLog()
            );
            ProductionOutputInvalidator.refresh(
                Objects.requireNonNull(project, "Maven did not inject the current project."),
                current.getUserProperties(),
                getLog()
            );
            ModuleTestPlanExporter.write(Objects.requireNonNull(project), current);
        }
        catch (IOException | RuntimeException exception)
        {
            throw new MojoExecutionException(
                "Cannot protect LightKeeper producer outputs: " + exception.getMessage(),
                exception
            );
        }
    }
}
