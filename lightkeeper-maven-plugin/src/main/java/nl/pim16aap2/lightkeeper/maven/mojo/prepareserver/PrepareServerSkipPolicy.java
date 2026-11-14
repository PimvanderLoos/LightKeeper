package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Evaluates the configured Failsafe executions instead of treating every test-skip flag as equivalent. */
final class PrepareServerSkipPolicy
{
    /** Canonical Maven coordinate key for Failsafe. */
    static final String FAILSAFE_KEY = "org.apache.maven.plugins:maven-failsafe-plugin";

    private PrepareServerSkipPolicy()
    {
    }

    static boolean shouldSkip(
        MavenProject project,
        Function<String, @Nullable Object> evaluate,
        boolean skipTests
    )
    {
        final Plugin failsafe = project.getPlugin(FAILSAFE_KEY);
        if (failsafe == null)
        {
            return false;
        }
        final String version = failsafe.getVersion();
        final boolean legacySkipTests = version != null &&
            new ComparableVersion(Objects.requireNonNull(evaluate.apply(version)).toString())
                .compareTo(new ComparableVersion("3.6.0")) < 0;
        final List<PluginExecution> executions = failsafe.getExecutions().stream()
            .filter(execution -> execution.getGoals().contains("integration-test"))
            .toList();
        if (executions.isEmpty())
        {
            return false;
        }
        return executions.stream().allMatch(execution ->
        {
            final Xpp3Dom effective = Xpp3Dom.mergeXpp3Dom(
                copy(execution.getConfiguration()),
                copy(failsafe.getConfiguration())
            );
            return configured(effective, "skip", false, evaluate) ||
                configured(effective, "skipITs", false, evaluate) ||
                configured(effective, "skipTests", legacySkipTests && skipTests, evaluate);
        });
    }

    private static Xpp3Dom copy(@Nullable Object configuration)
    {
        return configuration instanceof Xpp3Dom dom ? new Xpp3Dom(dom) : new Xpp3Dom("configuration");
    }

    private static boolean configured(
        Xpp3Dom configuration,
        String name,
        boolean fallback,
        Function<String, @Nullable Object> evaluate
    )
    {
        final Xpp3Dom value = configuration.getChild(name);
        if (value == null || value.getValue() == null)
        {
            return fallback;
        }
        final Object evaluated = evaluate.apply(value.getValue());
        final String resolved = evaluated == null ? "false" : evaluated.toString().trim();
        if (!resolved.equalsIgnoreCase("true") && !resolved.equalsIgnoreCase("false"))
        {
            throw new IllegalArgumentException("Cannot evaluate Failsafe '" + name + "' as a boolean: " + resolved);
        }
        return Boolean.parseBoolean(resolved);
    }
}
