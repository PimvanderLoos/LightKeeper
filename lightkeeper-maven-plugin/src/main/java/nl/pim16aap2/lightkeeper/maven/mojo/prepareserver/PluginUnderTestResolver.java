package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.provisioning.assets.ResolvedPluginArtifact;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Selects actual packaged Maven artifacts without guessing output filenames or scanning directories. */
final class PluginUnderTestResolver
{
    ResolvedPluginArtifact resolve(
        MavenProject project,
        List<MavenProject> reactorProjects,
        @Nullable PluginUnderTestConfig selection
    )
        throws MojoExecutionException
    {
        final String artifactId = selection == null ? null : normalized(selection.artifactId());
        final String groupId = selection == null ? null : normalized(selection.groupId());
        final String classifier = selection == null ? null : normalized(selection.classifier());
        final Path path = selection == null ? null : selection.path();
        if (path != null)
        {
            if (artifactId != null || groupId != null || classifier != null)
            {
                throw failure(project, "pluginUnderTest.path cannot be combined with artifact coordinates");
            }
            final Path resolved = path.isAbsolute() ? path : project.getBasedir().toPath().resolve(path);
            return resolved(project, resolved, "path:" + resolved);
        }
        if (artifactId == null)
        {
            if (groupId != null)
            {
                throw failure(project, "pluginUnderTest.groupId requires an artifactId");
            }
            return fromProject(project, project, classifier);
        }

        final List<Dependency> matches = project.getDependencies().stream()
            .filter(dependency -> artifactId.equals(dependency.getArtifactId()))
            .filter(dependency -> groupId == null || groupId.equals(dependency.getGroupId()))
            .filter(dependency -> classifier == null || classifier.equals(normalized(dependency.getClassifier())))
            .toList();
        if (matches.size() != 1)
        {
            throw failure(
                project,
                "selector '%s:%s:%s' matches %d declared dependencies; declare exactly one dependency or add "
                    .formatted(groupId, artifactId, classifier, matches.size())
                    + "pluginUnderTest.groupId/classifier to disambiguate"
            );
        }
        final Dependency dependency = matches.getFirst();
        final String selectedClassifier = normalized(dependency.getClassifier());
        for (final MavenProject reactorProject : reactorProjects)
        {
            if (dependency.getGroupId().equals(reactorProject.getGroupId()) &&
                dependency.getArtifactId().equals(reactorProject.getArtifactId()) &&
                dependency.getVersion().equals(reactorProject.getVersion()))
            {
                return fromProject(project, reactorProject, selectedClassifier);
            }
        }

        final Artifact artifact = project.getArtifacts().stream()
            .filter(candidate -> dependency.getGroupId().equals(candidate.getGroupId()))
            .filter(candidate -> dependency.getArtifactId().equals(candidate.getArtifactId()))
            .filter(candidate -> dependency.getVersion().equals(candidate.getVersion()))
            .filter(candidate -> Objects.equals(selectedClassifier, normalized(candidate.getClassifier())))
            .filter(candidate -> Objects.requireNonNullElse(dependency.getType(), "jar").equals(candidate.getType()))
            .findFirst()
            .orElseThrow(() -> failure(
                project, "declared dependency has no resolved packaged artifact: " + dependency
            ));
        return fromArtifact(project, artifact);
    }

    private static ResolvedPluginArtifact fromProject(
        MavenProject consumer,
        MavenProject producer,
        @Nullable String classifier
    )
        throws MojoExecutionException
    {
        if (classifier == null)
        {
            final Artifact artifact = producer.getArtifact();
            if (artifact == null)
            {
                throw failure(consumer, "project '" + producer.getId() + "' has no main artifact after package");
            }
            return fromArtifact(consumer, artifact);
        }
        final Artifact artifact = producer.getAttachedArtifacts().stream()
            .filter(candidate -> classifier.equals(candidate.getClassifier()))
            .filter(candidate -> "jar".equals(candidate.getType()))
            .findFirst()
            .orElseThrow(() -> failure(
                consumer,
                "project '%s' has no attached JAR with classifier '%s'".formatted(producer.getId(), classifier)
            ));
        return fromArtifact(consumer, artifact);
    }

    private static ResolvedPluginArtifact fromArtifact(MavenProject consumer, Artifact artifact)
        throws MojoExecutionException
    {
        final File file = artifact.getFile();
        if (file == null)
        {
            throw failure(
                consumer, "selected artifact '" + artifact + "' has no file; run the package lifecycle first"
            );
        }
        return resolved(consumer, file.toPath(), "maven:" + artifact);
    }

    private static ResolvedPluginArtifact resolved(MavenProject consumer, Path selectedPath, String identity)
        throws MojoExecutionException
    {
        final Path absolutePath = selectedPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolutePath) || !Files.isReadable(absolutePath))
        {
            throw failure(
                consumer, "selected artifact '" + identity + "' is not a readable JAR file at '" + absolutePath + "'"
            );
        }
        return new ResolvedPluginArtifact(absolutePath, absolutePath.getFileName().toString(), identity);
    }

    private static @Nullable String normalized(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MojoExecutionException failure(MavenProject project, String reason)
    {
        return new MojoExecutionException(
            "Cannot select plugin under test for module '%s': %s. Use pluginUnderTest.artifactId "
                .formatted(project.getId(), reason)
                + "(with a declared dependency), pluginUnderTest.classifier, pluginUnderTest.path, or serverOnly=true."
        );
    }
}
