package nl.pim16aap2.lightkeeper.maven;

import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.ResolvedPluginArtifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves plugin artifacts from local paths or Maven coordinates.
 */
final class PrepareServerPluginArtifactResolver
{
    List<ResolvedPluginArtifact> resolvePluginArtifacts(
        List<PluginArtifactSpec> specs,
        RepositorySystem repositorySystem,
        RepositorySystemSession repositorySystemSession,
        List<RemoteRepository> remoteProjectRepositories)
        throws MojoExecutionException
    {
        final List<ResolvedPluginArtifact> resolvedPluginArtifacts = new ArrayList<>();
        for (final PluginArtifactSpec spec : specs)
        {
            if (spec.sourceType() == PluginArtifactSpec.SourceType.PATH)
            {
                final Path sourceJar = Objects.requireNonNull(spec.path());
                final String outputFileName = spec.renameTo() == null
                    ? sourceJar.getFileName().toString()
                    : spec.renameTo();
                resolvedPluginArtifacts.add(new ResolvedPluginArtifact(
                    sourceJar,
                    outputFileName,
                    "path:" + sourceJar
                ));
                continue;
            }

            resolvedPluginArtifacts.addAll(resolveMavenPluginArtifacts(
                spec,
                repositorySystem,
                repositorySystemSession,
                remoteProjectRepositories
            ));
        }

        return resolvedPluginArtifacts;
    }

    private List<ResolvedPluginArtifact> resolveMavenPluginArtifacts(
        PluginArtifactSpec spec,
        RepositorySystem repositorySystem,
        RepositorySystemSession repositorySystemSession,
        List<RemoteRepository> remoteProjectRepositories)
        throws MojoExecutionException
    {
        final Artifact rootArtifact = new DefaultArtifact(
            Objects.requireNonNull(spec.groupId()),
            Objects.requireNonNull(spec.artifactId()),
            Objects.requireNonNullElse(spec.classifier(), ""),
            Objects.requireNonNull(spec.type()),
            Objects.requireNonNull(spec.version())
        );

        if (!spec.includeTransitive())
        {
            final ArtifactRequest request = new ArtifactRequest()
                .setArtifact(rootArtifact)
                .setRepositories(remoteProjectRepositories);
            try
            {
                final ArtifactResult result = repositorySystem.resolveArtifact(repositorySystemSession, request);
                final Path sourceJar = result.getArtifact().getFile().toPath();
                final String outputFileName = spec.renameTo() == null
                    ? sourceJar.getFileName().toString()
                    : spec.renameTo();
                return List.of(new ResolvedPluginArtifact(
                    sourceJar,
                    outputFileName,
                    "maven:" + result.getArtifact()
                ));
            }
            catch (ArtifactResolutionException exception)
            {
                throw new MojoExecutionException(
                    "Failed to resolve plugin artifact '%s'.".formatted(rootArtifact),
                    exception
                );
            }
        }

        final CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(rootArtifact, JavaScopes.RUNTIME));
        collectRequest.setRepositories(remoteProjectRepositories);

        final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
        try
        {
            final DependencyResult dependencyResult = repositorySystem.resolveDependencies(
                repositorySystemSession,
                dependencyRequest
            );
            final List<ResolvedPluginArtifact> resolvedPluginArtifacts = new ArrayList<>();
            for (final ArtifactResult artifactResult : dependencyResult.getArtifactResults())
            {
                final Artifact artifact = artifactResult.getArtifact();
                final Path sourceJar = artifact.getFile().toPath();
                resolvedPluginArtifacts.add(new ResolvedPluginArtifact(
                    sourceJar,
                    sourceJar.getFileName().toString(),
                    "maven:" + artifact
                ));
            }
            return resolvedPluginArtifacts;
        }
        catch (DependencyResolutionException exception)
        {
            throw new MojoExecutionException(
                "Failed to resolve transitive plugin artifacts for '%s'.".formatted(rootArtifact),
                exception
            );
        }
    }
}
