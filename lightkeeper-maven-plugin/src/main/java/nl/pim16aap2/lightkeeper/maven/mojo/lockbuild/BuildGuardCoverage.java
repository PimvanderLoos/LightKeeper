package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/** Canonical coverage rule for producer guards, shared by plan export and the read-only setup doctor. */
public final class BuildGuardCoverage
{
    /** Maven coordinate identifying LightKeeper's preparation and producer-guard goals. */
    public static final String PLUGIN_KEY = "nl.pim16aap2.lightkeeper:lightkeeper-maven-plugin";

    private BuildGuardCoverage()
    {
    }

    /**
     * Finds active bound preparation variants in Maven's effective model.
     *
     * @param project
     *     Module to inspect.
     * @return Executions that actually bind server preparation; phase {@code none} is excluded.
     */
    public static List<PluginExecution> preparations(MavenProject project)
    {
        final Plugin plugin = project.getPlugin(PLUGIN_KEY);
        return plugin == null ? List.of() : plugin.getExecutions().stream()
            .filter(execution -> !"none".equals(execution.getPhase()))
            .filter(execution -> execution.getGoals().contains("prepare-server"))
            .toList();
    }

    /**
     * Finds the selected test module and non-POM upstream producers without an early output guard.
     *
     * @param project
     *     Test module owning a preparation.
     * @param session
     *     Current Maven invocation and its selected dependency graph.
     * @return Unguarded modules in validation order, without modifying the Maven model.
     */
    public static List<MavenProject> missing(MavenProject project, MavenSession session)
    {
        if (!hasEarlyGuard(project))
        {
            return List.of(project);
        }
        final List<MavenProject> candidates = new ArrayList<>();
        candidates.add(project);
        final var graph = session.getProjectDependencyGraph();
        if (graph != null)
        {
            graph.getUpstreamProjects(project, true).stream()
                .filter(upstream -> !"pom".equals(upstream.getPackaging()))
                .forEach(candidates::add);
        }
        return candidates.stream().filter(candidate -> !hasEarlyGuard(candidate)).distinct().toList();
    }

    /**
     * Requires guards before production outputs change, preserving the plan export's fail-fast behavior.
     *
     * @param project
     *     Test module owning a preparation.
     * @param session
     *     Current Maven invocation.
     * @throws IllegalStateException
     *     If the module or a selected upstream producer lacks an early guard.
     */
    public static void require(MavenProject project, MavenSession session)
    {
        final List<MavenProject> missing = missing(project, session);
        if (!missing.isEmpty())
        {
            throw new IllegalStateException("Producer module '" + missing.getFirst().getId()
                + "' has no early LightKeeper lock-build execution. Inherit the lightweight lock-build goal "
                + "from the reactor parent so packaging and snapshot capture cannot overlap another build.");
        }
    }

    private static boolean hasEarlyGuard(MavenProject project)
    {
        final Plugin plugin = project.getPlugin(PLUGIN_KEY);
        return plugin != null && plugin.getExecutions().stream().anyMatch(execution ->
            execution.getGoals().contains("lock-build") && (execution.getPhase() == null ||
                "initialize".equals(execution.getPhase()) || "validate".equals(execution.getPhase())));
    }
}
