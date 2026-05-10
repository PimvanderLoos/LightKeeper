package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.ModrinthDownloadsClient;
import nl.pim16aap2.lightkeeper.maven.ModrinthPluginMetadata;
import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.maven.util.CacheKeyUtil;
import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves plugin artifacts from local paths, Maven coordinates, URL downloads, and Modrinth downloads.
 */
final class PrepareServerPluginArtifactResolver
{
    List<ResolvedPluginArtifact> resolvePluginArtifacts(
        List<PluginArtifactSpec> specs,
        RepositorySystem repositorySystem,
        RepositorySystemSession repositorySystemSession,
        List<RemoteRepository> remoteProjectRepositories,
        Path pluginArtifactCacheDirectoryRoot,
        String userAgent,
        Log log)
        throws MojoExecutionException
    {
        final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        final ModrinthDownloadsClient modrinthDownloadsClient = new ModrinthDownloadsClient(log, userAgent, httpClient);
        return resolvePluginArtifacts(
            specs,
            repositorySystem,
            repositorySystemSession,
            remoteProjectRepositories,
            pluginArtifactCacheDirectoryRoot,
            userAgent,
            httpClient,
            modrinthDownloadsClient,
            log
        );
    }

    List<ResolvedPluginArtifact> resolvePluginArtifacts(
        List<PluginArtifactSpec> specs,
        RepositorySystem repositorySystem,
        RepositorySystemSession repositorySystemSession,
        List<RemoteRepository> remoteProjectRepositories,
        Path pluginArtifactCacheDirectoryRoot,
        String userAgent,
        HttpClient httpClient,
        ModrinthDownloadsClient modrinthDownloadsClient,
        Log log)
        throws MojoExecutionException
    {
        final List<ResolvedPluginArtifact> resolvedPluginArtifacts = new ArrayList<>();
        for (final PluginArtifactSpec spec : specs)
        {
            switch (spec.sourceType())
            {
                case PATH ->
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
                }
                case MAVEN -> resolvedPluginArtifacts.addAll(resolveMavenPluginArtifacts(
                    spec,
                    repositorySystem,
                    repositorySystemSession,
                    remoteProjectRepositories
                ));
                case URL -> resolvedPluginArtifacts.add(resolveUrlPluginArtifact(
                    spec,
                    pluginArtifactCacheDirectoryRoot,
                    userAgent,
                    httpClient,
                    log
                ));
                case MODRINTH -> resolvedPluginArtifacts.add(resolveModrinthPluginArtifact(
                    spec,
                    pluginArtifactCacheDirectoryRoot,
                    userAgent,
                    httpClient,
                    modrinthDownloadsClient,
                    log
                ));
            }
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

    private ResolvedPluginArtifact resolveUrlPluginArtifact(
        PluginArtifactSpec spec,
        Path pluginArtifactCacheDirectoryRoot,
        String userAgent,
        HttpClient httpClient,
        Log log)
        throws MojoExecutionException
    {
        final URI uri = Objects.requireNonNull(spec.uri());
        return downloadToCache(
            uri,
            Objects.requireNonNull(spec.renameTo()),
            "url:" + uri.normalize(),
            "sha256",
            Objects.requireNonNull(spec.sha256()),
            pluginArtifactCacheDirectoryRoot,
            userAgent,
            httpClient,
            log
        );
    }

    private ResolvedPluginArtifact resolveModrinthPluginArtifact(
        PluginArtifactSpec spec,
        Path pluginArtifactCacheDirectoryRoot,
        String userAgent,
        HttpClient httpClient,
        ModrinthDownloadsClient modrinthDownloadsClient,
        Log log)
        throws MojoExecutionException
    {
        final ModrinthPluginMetadata metadata = modrinthDownloadsClient.resolvePluginFile(spec);
        final String outputFileName = spec.renameTo() == null
            ? PrepareServerInputResolver.validatePluginFileName(metadata.fileName(), "Modrinth filename")
            : spec.renameTo();
        return downloadToCache(
            metadata.downloadUri(),
            outputFileName,
            "modrinth:%s:%s:%s".formatted(metadata.versionId(), metadata.versionNumber(), metadata.fileName()),
            "sha512",
            metadata.sha512(),
            pluginArtifactCacheDirectoryRoot,
            userAgent,
            httpClient,
            log
        );
    }

    private ResolvedPluginArtifact downloadToCache(
        URI uri,
        String outputFileName,
        String identity,
        String hashAlgorithm,
        String expectedHash,
        Path pluginArtifactCacheDirectoryRoot,
        String userAgent,
        HttpClient httpClient,
        Log log)
        throws MojoExecutionException
    {
        final String normalizedHash = expectedHash.toLowerCase(Locale.ROOT);
        final String cacheKey = CacheKeyUtil.createCacheKey(List.of(
            hashAlgorithm,
            identity,
            outputFileName,
            normalizedHash
        ));
        final Path cacheDirectory = pluginArtifactCacheDirectoryRoot.resolve(cacheKey);
        final Path cachedJar = cacheDirectory.resolve(outputFileName);
        if (Files.isRegularFile(cachedJar))
        {
            verifyHash(cachedJar, hashAlgorithm, normalizedHash, "cached plugin artifact");
            log.info("LK_PLUGIN: Reusing cached plugin artifact '%s'.".formatted(cachedJar));
            return new ResolvedPluginArtifact(cachedJar, outputFileName, identity);
        }

        FileUtil.createDirectories(cacheDirectory, "plugin artifact cache directory");
        final Path temporaryDownload;
        try
        {
            temporaryDownload = Files.createTempFile(cacheDirectory, outputFileName, ".tmp");
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to create temporary plugin artifact download in '%s'.".formatted(cacheDirectory),
                exception
            );
        }

        try
        {
            download(uri, temporaryDownload, userAgent, httpClient);
            verifyHash(temporaryDownload, hashAlgorithm, normalizedHash, "downloaded plugin artifact");
            try
            {
                Files.move(
                    temporaryDownload,
                    cachedJar,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
            catch (AtomicMoveNotSupportedException exception)
            {
                Files.move(
                    temporaryDownload,
                    cachedJar,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
            log.info("LK_PLUGIN: Cached plugin artifact '%s' from '%s'.".formatted(cachedJar, uri));
            return new ResolvedPluginArtifact(cachedJar, outputFileName, identity);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to cache plugin artifact from '%s' to '%s'.".formatted(uri, cachedJar),
                exception
            );
        }
        finally
        {
            try
            {
                Files.deleteIfExists(temporaryDownload);
            }
            catch (IOException ignored)
            {
                log.debug("Failed to delete temporary plugin artifact download '%s'.".formatted(temporaryDownload));
            }
        }
    }

    private static void download(URI uri, Path target, String userAgent, HttpClient httpClient)
        throws MojoExecutionException
    {
        final HttpRequest request = HttpRequest.newBuilder(uri)
            .header("User-Agent", userAgent)
            .timeout(Duration.ofMinutes(2))
            .GET()
            .build();
        final HttpResponse<Path> response;
        try
        {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Failed to download plugin artifact from '%s'.".formatted(uri), exception);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException("Failed to download plugin artifact from '%s'.".formatted(uri), exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new MojoExecutionException(
                "Plugin artifact download from '%s' failed with status %d.".formatted(uri, response.statusCode())
            );
    }

    private static void verifyHash(Path path, String hashAlgorithm, String expectedHash, String description)
        throws MojoExecutionException
    {
        final String actualHash = switch (hashAlgorithm)
        {
            case "sha256" -> HashUtil.sha256(path);
            case "sha512" -> HashUtil.sha512(path);
            default -> throw new IllegalArgumentException("Unsupported hash algorithm: " + hashAlgorithm);
        };
        if (!expectedHash.equalsIgnoreCase(actualHash))
        {
            throw new MojoExecutionException(
                "%s '%s' failed %s verification. Expected %s but got %s."
                    .formatted(description, path, displayHashAlgorithm(hashAlgorithm), expectedHash, actualHash)
            );
        }
    }

    private static String displayHashAlgorithm(String hashAlgorithm)
    {
        return switch (hashAlgorithm)
        {
            case "sha256" -> "SHA-256";
            case "sha512" -> "SHA-512";
            default -> hashAlgorithm.toUpperCase(Locale.ROOT);
        };
    }
}
