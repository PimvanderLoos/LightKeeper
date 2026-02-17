package nl.pim16aap2.lightkeeper.maven;

import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import nl.pim16aap2.lightkeeper.maven.serverprovider.ServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.PaperServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.SpigotServerProvider;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrepareServerMojoInternalTest
{
    @Test
    void resolveServerSetup_shouldResolvePaperBranchUsingInjectedClient(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PaperDownloadsClient paperDownloadsClient = mock(PaperDownloadsClient.class);
        final PaperBuildMetadata paperBuildMetadata = new PaperBuildMetadata(
            "1.21.11",
            116L,
            URI.create("https://example.com/paper.jar"),
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
        when(paperDownloadsClient.resolveBuild("latest-supported")).thenReturn(paperBuildMetadata);

        final TestPrepareServerMojo mojo = new TestPrepareServerMojo(
            paperDownloadsClient,
            mock(SpigotDownloadsClient.class)
        );
        configureRequiredFields(mojo, tempDirectory, "paper");

        final Object context = invokePrivate(mojo, "buildExecutionContext", new Class<?>[0]);
        final Object agentMetadata = invokePrivate(
            mojo,
            "resolveAgentMetadata",
            new Class<?>[]{Path.class},
            new Object[]{null}
        );

        // execute
        final Object setup = invokePrivate(
            mojo,
            "resolveServerSetup",
            new Class<?>[]{context.getClass(), agentMetadata.getClass(), String.class, String.class},
            context,
            agentMetadata,
            "auth-token",
            "v1.1"
        );

        // verify
        assertThat((String) invokeRecordAccessor(setup, "manifestServerVersion")).isEqualTo("1.21.11");
        assertThat((Long) invokeRecordAccessor(setup, "manifestBuildId")).isEqualTo(116L);
        assertThat((String) invokeRecordAccessor(setup, "cacheKey")).isNotBlank();
        assertThat((Object) invokeRecordAccessor(setup, "serverProvider")).isInstanceOf(PaperServerProvider.class);
    }

    @Test
    void resolveServerSetup_shouldResolveSpigotBranchUsingInjectedClient(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PaperDownloadsClient paperDownloadsClient = mock(PaperDownloadsClient.class);
        final SpigotDownloadsClient spigotDownloadsClient = mock(SpigotDownloadsClient.class);
        final SpigotBuildMetadata spigotBuildMetadata = new SpigotBuildMetadata(
            "1.21.11",
            URI.create("https://example.com/buildtools.jar"),
            "buildtools-id"
        );
        when(spigotDownloadsClient.resolveBuild("latest-supported")).thenReturn(spigotBuildMetadata);

        final TestPrepareServerMojo mojo = new TestPrepareServerMojo(paperDownloadsClient, spigotDownloadsClient);
        configureRequiredFields(mojo, tempDirectory, "spigot");

        final Object context = invokePrivate(mojo, "buildExecutionContext", new Class<?>[0]);
        final Object agentMetadata = invokePrivate(
            mojo,
            "resolveAgentMetadata",
            new Class<?>[]{Path.class},
            new Object[]{null}
        );

        // execute
        final Object setup = invokePrivate(
            mojo,
            "resolveServerSetup",
            new Class<?>[]{context.getClass(), agentMetadata.getClass(), String.class, String.class},
            context,
            agentMetadata,
            "auth-token",
            "v1.1"
        );

        // verify
        assertThat((String) invokeRecordAccessor(setup, "manifestServerVersion")).isEqualTo("1.21.11");
        assertThat((Long) invokeRecordAccessor(setup, "manifestBuildId")).isEqualTo(0L);
        assertThat((String) invokeRecordAccessor(setup, "cacheKey")).isNotBlank();
        assertThat((Object) invokeRecordAccessor(setup, "serverProvider")).isInstanceOf(SpigotServerProvider.class);
    }

    @Test
    void buildExecutionContext_shouldResolveConfiguredFieldsAndSpecs(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        configureRequiredFields(mojo, tempDirectory, "paper");

        // execute
        final Object context = invokePrivate(mojo, "buildExecutionContext", new Class<?>[0]);

        // verify
        assertThat((String) invokeRecordAccessor(context, "normalizedServerType")).isEqualTo("paper");
        assertThat((String) invokeRecordAccessor(context, "serverVersion")).isEqualTo("latest-supported");
        assertThat((String) invokeRecordAccessor(context, "userAgent")).isEqualTo("LightKeeper/Test");
        assertThat((List<?>) invokeRecordAccessor(context, "worldInputSpecs")).isEmpty();
        assertThat((List<?>) invokeRecordAccessor(context, "pluginArtifactSpecs")).isEmpty();
    }

    @Test
    void validateConfiguration_shouldAcceptValidConfiguration()
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "serverType", "paper");
        setField(mojo, "userAgent", "LightKeeper/Test");
        setField(mojo, "serverStartMaxAttempts", 1);
        setField(mojo, "jarCacheExpiryDays", 0);
        setField(mojo, "baseServerCacheExpiryDays", 0);

        // execute + verify
        invokePrivate(mojo, "validateConfiguration", new Class<?>[0]);
    }

    @Test
    void validateConfiguration_shouldThrowExceptionWhenServerTypeIsUnsupported()
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "serverType", "invalid");
        setField(mojo, "userAgent", "LightKeeper/Test");
        setField(mojo, "serverStartMaxAttempts", 1);
        setField(mojo, "jarCacheExpiryDays", 0);
        setField(mojo, "baseServerCacheExpiryDays", 0);

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "validateConfiguration", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Unsupported server type");
    }

    @Test
    void validateConfiguration_shouldThrowExceptionWhenUserAgentIsBlank()
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "serverType", "paper");
        setField(mojo, "userAgent", "   ");
        setField(mojo, "serverStartMaxAttempts", 1);
        setField(mojo, "jarCacheExpiryDays", 0);
        setField(mojo, "baseServerCacheExpiryDays", 0);

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "validateConfiguration", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("lightkeeper.userAgent");
    }

    @Test
    void resolveWorldInputSpecs_shouldReturnValidatedWorldSpecifications(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path folderWorld = Files.createDirectories(tempDirectory.resolve("world-folder"));
        final Path archiveWorld = Files.writeString(tempDirectory.resolve("world.zip"), "fixture");

        final PrepareServerMojo.WorldInputConfig folderConfig = new PrepareServerMojo.WorldInputConfig();
        setField(folderConfig, "name", "fixture_world");
        setField(folderConfig, "sourceType", "folder");
        setField(folderConfig, "sourcePath", folderWorld);
        setField(folderConfig, "environment", "nether");
        setField(folderConfig, "worldType", "flat");
        setField(folderConfig, "seed", 42L);
        setField(folderConfig, "overwrite", Boolean.FALSE);
        setField(folderConfig, "loadOnStartup", Boolean.TRUE);

        final PrepareServerMojo.WorldInputConfig archiveConfig = new PrepareServerMojo.WorldInputConfig();
        setField(archiveConfig, "name", "archive_world");
        setField(archiveConfig, "sourceType", "archive");
        setField(archiveConfig, "sourcePath", archiveWorld);

        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "worlds", List.of(folderConfig, archiveConfig));

        // execute
        final List<WorldInputSpec> result = invokePrivate(mojo, "resolveWorldInputSpecs", new Class<?>[0]);

        // verify
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().name()).isEqualTo("fixture_world");
        assertThat(result.getFirst().sourceType()).isEqualTo(WorldInputSpec.SourceType.FOLDER);
        assertThat(result.getFirst().sourcePath()).isEqualTo(folderWorld.toAbsolutePath().normalize());
        assertThat(result.getFirst().overwrite()).isFalse();
        assertThat(result.getFirst().loadOnStartup()).isTrue();
        assertThat(result.getFirst().environment()).isEqualTo("NETHER");
        assertThat(result.getFirst().worldType()).isEqualTo("FLAT");
        assertThat(result.getFirst().seed()).isEqualTo(42L);

        assertThat(result.get(1).name()).isEqualTo("archive_world");
        assertThat(result.get(1).sourceType()).isEqualTo(WorldInputSpec.SourceType.ARCHIVE);
        assertThat(result.get(1).environment()).isEqualTo("NORMAL");
        assertThat(result.get(1).worldType()).isEqualTo("NORMAL");
        assertThat(result.get(1).seed()).isZero();
    }

    @Test
    void resolveWorldInputSpecs_shouldThrowExceptionWhenWorldNameContainsTraversal(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerMojo.WorldInputConfig config = new PrepareServerMojo.WorldInputConfig();
        setField(config, "name", "../bad-world");
        setField(config, "sourceType", "folder");
        setField(config, "sourcePath", Files.createDirectories(tempDirectory.resolve("world")));
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "worlds", List.of(config));

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "resolveWorldInputSpecs", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Invalid world name");
    }

    @Test
    void resolveWorldInputSpecs_shouldThrowExceptionWhenWorldNameIsDot(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerMojo.WorldInputConfig config = new PrepareServerMojo.WorldInputConfig();
        setField(config, "name", ".");
        setField(config, "sourceType", "folder");
        setField(config, "sourcePath", Files.createDirectories(tempDirectory.resolve("world")));
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "worlds", List.of(config));

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "resolveWorldInputSpecs", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Invalid world name");
    }

    @Test
    void resolvePluginArtifactSpecs_shouldReturnPathAndMavenSpecifications(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path localPluginJar = Files.writeString(tempDirectory.resolve("local-plugin.jar"), "plugin");

        final PrepareServerMojo.PluginArtifactConfig pathConfig = new PrepareServerMojo.PluginArtifactConfig();
        setField(pathConfig, "sourceType", "PATH");
        setField(pathConfig, "path", localPluginJar);
        setField(pathConfig, "renameTo", "renamed-plugin.jar");

        final PrepareServerMojo.PluginArtifactConfig mavenConfig = new PrepareServerMojo.PluginArtifactConfig();
        setField(mavenConfig, "sourceType", "MAVEN");
        setField(mavenConfig, "groupId", "com.example");
        setField(mavenConfig, "artifactId", "my-plugin");
        setField(mavenConfig, "version", "1.2.3");
        setField(mavenConfig, "type", "jar");
        setField(mavenConfig, "classifier", "all");
        setField(mavenConfig, "includeTransitive", Boolean.FALSE);

        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "plugins", List.of(pathConfig, mavenConfig));

        // execute
        final List<PluginArtifactSpec> specs = invokePrivate(mojo, "resolvePluginArtifactSpecs", new Class<?>[0]);

        // verify
        assertThat(specs).hasSize(2);
        assertThat(specs.getFirst().sourceType()).isEqualTo(PluginArtifactSpec.SourceType.PATH);
        assertThat(specs.getFirst().path()).isEqualTo(localPluginJar.toAbsolutePath().normalize());
        assertThat(specs.getFirst().renameTo()).isEqualTo("renamed-plugin.jar");
        assertThat(specs.get(1).sourceType()).isEqualTo(PluginArtifactSpec.SourceType.MAVEN);
        assertThat(specs.get(1).groupId()).isEqualTo("com.example");
        assertThat(specs.get(1).artifactId()).isEqualTo("my-plugin");
        assertThat(specs.get(1).version()).isEqualTo("1.2.3");
        assertThat(specs.get(1).type()).isEqualTo("jar");
        assertThat(specs.get(1).classifier()).isEqualTo("all");
    }

    @Test
    void resolvePluginArtifactSpecs_shouldThrowExceptionWhenPathSourceDoesNotExist(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerMojo.PluginArtifactConfig config = new PrepareServerMojo.PluginArtifactConfig();
        setField(config, "sourceType", "path");
        setField(config, "path", tempDirectory.resolve("missing.jar"));

        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "plugins", List.of(config));

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "resolvePluginArtifactSpecs", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("does not exist as a regular file");
    }

    @Test
    void resolvePluginArtifactSpecs_shouldThrowExceptionWhenRenameToIsUsedWithTransitiveDependencies()
        throws Exception
    {
        // setup
        final PrepareServerMojo.PluginArtifactConfig config = new PrepareServerMojo.PluginArtifactConfig();
        setField(config, "sourceType", "maven");
        setField(config, "groupId", "com.example");
        setField(config, "artifactId", "test-plugin");
        setField(config, "version", "1.0.0");
        setField(config, "includeTransitive", Boolean.TRUE);
        setField(config, "renameTo", "invalid.jar");

        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "plugins", List.of(config));

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "resolvePluginArtifactSpecs", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("cannot set renameTo when includeTransitive=true");
    }

    @Test
    void resolvePluginArtifacts_shouldResolvePathAndMavenArtifacts(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path pathPlugin = Files.writeString(tempDirectory.resolve("path-plugin.jar"), "path");
        final Path mavenPlugin = Files.writeString(tempDirectory.resolve("maven-plugin.jar"), "maven");

        final PluginArtifactSpec pathSpec = new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.PATH,
            pathPlugin,
            null,
            null,
            null,
            null,
            "jar",
            false,
            "path-renamed.jar"
        );
        final PluginArtifactSpec mavenSpec = new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.MAVEN,
            null,
            "com.example",
            "plugin",
            "1.0.0",
            null,
            "jar",
            false,
            null
        );

        final ArtifactRequest artifactRequest = new ArtifactRequest();
        final Artifact artifact = new DefaultArtifact("com.example", "plugin", "", "jar", "1.0.0")
            .setFile(mavenPlugin.toFile());
        final ArtifactResult artifactResult = new ArtifactResult(artifactRequest).setArtifact(artifact);

        final RepositorySystem repositorySystem = mock();
        final RepositorySystemSession repositorySystemSession = mock();
        when(repositorySystem.resolveArtifact(any(), any())).thenReturn(artifactResult);

        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "repositorySystem", repositorySystem);
        setField(mojo, "repositorySystemSession", repositorySystemSession);
        setField(mojo, "remoteProjectRepositories", List.of());

        // execute
        final List<ResolvedPluginArtifact> resolved = invokePrivate(
            mojo,
            "resolvePluginArtifacts",
            new Class<?>[]{List.class},
            List.of(pathSpec, mavenSpec)
        );

        // verify
        assertThat(resolved).hasSize(2);
        assertThat(resolved.getFirst().sourceJar()).isEqualTo(pathPlugin);
        assertThat(resolved.getFirst().outputFileName()).isEqualTo("path-renamed.jar");
        assertThat(resolved.get(1).sourceJar()).isEqualTo(mavenPlugin);
        assertThat(resolved.get(1).outputFileName()).isEqualTo("maven-plugin.jar");
    }

    @Test
    void resolvePluginArtifacts_shouldResolveTransitiveMavenArtifacts(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path dependencyJarA = Files.writeString(tempDirectory.resolve("dependency-a.jar"), "a");
        final Path dependencyJarB = Files.writeString(tempDirectory.resolve("dependency-b.jar"), "b");

        final PluginArtifactSpec transitiveSpec = new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.MAVEN,
            null,
            "com.example",
            "bundle",
            "1.0.0",
            null,
            "jar",
            true,
            null
        );

        final ArtifactResult artifactResultA = new ArtifactResult(new ArtifactRequest()).setArtifact(
            new DefaultArtifact("com.example", "dependency-a", "", "jar", "1.0.0").setFile(dependencyJarA.toFile())
        );
        final ArtifactResult artifactResultB = new ArtifactResult(new ArtifactRequest()).setArtifact(
            new DefaultArtifact("com.example", "dependency-b", "", "jar", "1.0.0").setFile(dependencyJarB.toFile())
        );
        final DependencyResult dependencyResult = new DependencyResult(new DependencyRequest())
            .setArtifactResults(List.of(artifactResultA, artifactResultB));

        final RepositorySystem repositorySystem = mock();
        final RepositorySystemSession repositorySystemSession = mock();
        when(repositorySystem.resolveDependencies(any(), any())).thenReturn(dependencyResult);

        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "repositorySystem", repositorySystem);
        setField(mojo, "repositorySystemSession", repositorySystemSession);
        setField(mojo, "remoteProjectRepositories", List.of());

        // execute
        final List<ResolvedPluginArtifact> resolved = invokePrivate(
            mojo,
            "resolvePluginArtifacts",
            new Class<?>[]{List.class},
            List.of(transitiveSpec)
        );

        // verify
        assertThat(resolved).hasSize(2);
        assertThat(resolved)
            .extracting(ResolvedPluginArtifact::outputFileName)
            .containsExactlyInAnyOrder("dependency-a.jar", "dependency-b.jar");
    }

    @Test
    void resolvePluginArtifacts_shouldWrapRepositoryResolutionFailures()
        throws Exception
    {
        // setup
        final PluginArtifactSpec mavenSpec = new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.MAVEN,
            null,
            "com.example",
            "plugin",
            "1.0.0",
            null,
            "jar",
            false,
            null
        );

        final RepositorySystem repositorySystem = mock();
        final RepositorySystemSession repositorySystemSession = mock();
        when(repositorySystem.resolveArtifact(any(), any()))
            .thenThrow(new ArtifactResolutionException(List.of(), "boom"));

        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "repositorySystem", repositorySystem);
        setField(mojo, "repositorySystemSession", repositorySystemSession);
        setField(mojo, "remoteProjectRepositories", List.of());

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(
            mojo,
            "resolvePluginArtifacts",
            new Class<?>[]{List.class},
            List.of(mavenSpec)
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Failed to resolve plugin artifact");
    }

    @Test
    void resolvePluginArtifacts_shouldWrapTransitiveResolutionFailures()
        throws Exception
    {
        // setup
        final PluginArtifactSpec transitiveSpec = new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.MAVEN,
            null,
            "com.example",
            "bundle",
            "1.0.0",
            null,
            "jar",
            true,
            null
        );

        final RepositorySystem repositorySystem = mock();
        final RepositorySystemSession repositorySystemSession = mock();
        final DependencyResult dependencyResult = new DependencyResult(new DependencyRequest());
        when(repositorySystem.resolveDependencies(any(), any()))
            .thenThrow(new DependencyResolutionException(dependencyResult, new RuntimeException("boom")));

        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "repositorySystem", repositorySystem);
        setField(mojo, "repositorySystemSession", repositorySystemSession);
        setField(mojo, "remoteProjectRepositories", List.of());

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(
            mojo,
            "resolvePluginArtifacts",
            new Class<?>[]{List.class},
            List.of(transitiveSpec)
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Failed to resolve transitive plugin artifacts");
    }

    @Test
    void resolveAgentMetadata_shouldResolveNoAgentAndHashedAgentValues(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        final Path agentJar = Files.writeString(tempDirectory.resolve("agent.jar"), "agent-binary");

        // execute
        final Object noAgentMetadata = invokePrivate(
            mojo,
            "resolveAgentMetadata",
            new Class<?>[]{Path.class},
            new Object[]{null}
        );
        final Object hashedAgentMetadata = invokePrivate(
            mojo,
            "resolveAgentMetadata",
            new Class<?>[]{Path.class},
            agentJar
        );

        // verify
        final String noAgentCacheIdentity = invokeRecordAccessor(noAgentMetadata, "cacheIdentity");
        final String noAgentSha256 = invokeRecordAccessor(noAgentMetadata, "sha256");
        assertThat(noAgentCacheIdentity).isEqualTo("no-agent");
        assertThat(noAgentSha256).isNull();

        final String hashedCacheIdentity = invokeRecordAccessor(hashedAgentMetadata, "cacheIdentity");
        final String hashedSha256 = invokeRecordAccessor(hashedAgentMetadata, "sha256");
        assertThat(hashedCacheIdentity).startsWith("agent.jar:");
        assertThat(hashedSha256).matches("[a-f0-9]{64}");
    }

    @Test
    void resolveAgentMetadata_shouldThrowExceptionWhenJarDoesNotExist(@TempDir Path tempDirectory)
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        final Path missingAgentJar = tempDirectory.resolve("missing-agent.jar");

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(
            mojo,
            "resolveAgentMetadata",
            new Class<?>[]{Path.class},
            missingAgentJar
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("does not exist");
    }

    @Test
    void resolveUdsSocketPath_shouldFallbackToTemporaryDirectoryWhenPreferredPathIsTooLong(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        final StringBuilder longNameBuilder = new StringBuilder();
        longNameBuilder.repeat("very-long-segment-", 200);
        final Path veryLongDirectory = tempDirectory.resolve(longNameBuilder.toString());

        // execute
        final Path socketPath = invokePrivate(
            mojo,
            "resolveUdsSocketPath",
            new Class<?>[]{Path.class, String.class},
            veryLongDirectory,
            "0123456789abcdef0123456789abcdef"
        );

        // verify
        assertThat(socketPath.toString()).contains("lightkeeper-sockets");
        assertThat(socketPath.getFileName().toString()).startsWith("lk-01234567");
        assertThat(socketPath.getFileName().toString()).endsWith(".sock");
    }

    @Test
    void resolveUdsSocketPath_shouldUsePreferredPathWhenItFits(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        final Path preferredDirectory = Files.createDirectories(tempDirectory.resolve("socket-dir"));

        // execute
        final Path socketPath = invokePrivate(
            mojo,
            "resolveUdsSocketPath",
            new Class<?>[]{Path.class, String.class},
            preferredDirectory,
            "abcdef0123456789abcdef0123456789"
        );

        // verify
        assertThat(socketPath.toString()).startsWith(preferredDirectory.toAbsolutePath().toString());
        assertThat(socketPath.getFileName().toString()).startsWith("lk-abcdef01");
    }

    @Test
    void execute_shouldFailFastOnInvalidConfiguration()
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "serverType", "unsupported-type");
        setField(mojo, "userAgent", "LightKeeper/Test");
        setField(mojo, "serverStartMaxAttempts", 1);
        setField(mojo, "jarCacheExpiryDays", 0);
        setField(mojo, "baseServerCacheExpiryDays", 0);

        // execute + verify
        assertThatThrownBy(mojo::execute)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Unsupported server type");
    }

    @Test
    void execute_shouldPrepareServerAndWriteRuntimeManifestUsingResolvedSetup(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestPrepareServerMojo mojo = new TestPrepareServerMojo(
            mock(PaperDownloadsClient.class),
            mock(SpigotDownloadsClient.class)
        );
        configureRequiredFields(mojo, tempDirectory, "paper");

        final Path targetServerDirectory = tempDirectory.resolve("prepared-server");
        final Path targetJarPath = targetServerDirectory.resolve("paper-1.21.11.jar");
        final ServerProvider serverProvider = mock(ServerProvider.class);
        when(serverProvider.targetServerDirectoryPath()).thenReturn(targetServerDirectory);
        when(serverProvider.targetJarFilePath()).thenReturn(targetJarPath);

        mojo.setResolvedServerSetupForTests(
            new PrepareServerMojo.ResolvedServerSetup(
                serverProvider,
                "1.21.11",
                116L,
                "paper-cache-key",
                768
            )
        );
        mojo.setResolvedAgentMetadataForTests(new PrepareServerMojo.AgentMetadata("abc123", "agent-cache-id"));
        mojo.setResolvedSocketPathForTests(tempDirectory.resolve("sockets/lk-test.sock"));

        // execute
        mojo.execute();

        // verify
        verify(serverProvider).prepareServer();
        assertThat(mojo.installServerAssetsCalled()).isTrue();
        assertThat(mojo.installServerAssetsTargetDirectory()).isEqualTo(targetServerDirectory);
        assertThat(mojo.installServerAssetsPluginCount()).isZero();

        final Path runtimeManifestPath = tempDirectory.resolve("runtime-manifest.json");
        assertThat(runtimeManifestPath).isRegularFile();
        final String runtimeManifestJson = Files.readString(runtimeManifestPath);
        assertThat(runtimeManifestJson).contains("\"serverType\":\"paper\"");
        assertThat(runtimeManifestJson).contains("\"serverVersion\":\"1.21.11\"");
        assertThat(runtimeManifestJson).contains("\"paperBuildId\":116");
        assertThat(runtimeManifestJson).contains("\"cacheKey\":\"paper-cache-key\"");
        assertThat(runtimeManifestJson).contains("\"memoryMb\":768");
        assertThat(runtimeManifestJson).contains("agent-cache-id");
    }

    @Test
    void validateConfiguration_shouldThrowExceptionWhenServerStartAttemptsAreInvalid()
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "serverType", "paper");
        setField(mojo, "userAgent", "LightKeeper/Test");
        setField(mojo, "serverStartMaxAttempts", 0);
        setField(mojo, "jarCacheExpiryDays", 0);
        setField(mojo, "baseServerCacheExpiryDays", 0);

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "validateConfiguration", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("serverStartMaxAttempts");
    }

    @Test
    void validateConfiguration_shouldThrowExceptionWhenCacheExpiryDaysAreNegative()
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "serverType", "paper");
        setField(mojo, "userAgent", "LightKeeper/Test");
        setField(mojo, "serverStartMaxAttempts", 1);
        setField(mojo, "jarCacheExpiryDays", -1);
        setField(mojo, "baseServerCacheExpiryDays", 0);

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "validateConfiguration", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("jarCacheExpiryDays");
    }

    @Test
    void validateConfiguration_shouldThrowExceptionWhenExtraJvmArgsContainUnresolvedPlaceholder()
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "serverType", "paper");
        setField(mojo, "userAgent", "LightKeeper/Test");
        setField(mojo, "serverStartMaxAttempts", 1);
        setField(mojo, "jarCacheExpiryDays", 0);
        setField(mojo, "baseServerCacheExpiryDays", 0);
        setField(mojo, "extraJvmArgs", "-Dfoo=${project.version}");

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "validateConfiguration", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("unresolved Maven placeholder");
    }

    @Test
    void resolveWorldInputSpecs_shouldThrowExceptionWhenWorldEntryIsNull()
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "worlds", java.util.Arrays.asList((PrepareServerMojo.WorldInputConfig) null));

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "resolveWorldInputSpecs", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("world entry may not be null");
    }

    @Test
    void resolveWorldInputSpecs_shouldThrowExceptionWhenSourceTypeIsUnsupported(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerMojo.WorldInputConfig config = new PrepareServerMojo.WorldInputConfig();
        setField(config, "name", "world");
        setField(config, "sourceType", "bogus");
        setField(config, "sourcePath", Files.createDirectories(tempDirectory.resolve("world")));

        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "worlds", List.of(config));

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "resolveWorldInputSpecs", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Unsupported world sourceType");
    }

    @Test
    void resolvePluginArtifactSpecs_shouldThrowExceptionWhenPluginEntryIsNull()
        throws Exception
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "plugins", java.util.Arrays.asList((PrepareServerMojo.PluginArtifactConfig) null));

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "resolvePluginArtifactSpecs", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("plugin entry may not be null");
    }

    @Test
    void resolvePluginArtifactSpecs_shouldThrowExceptionWhenRenameToIsInvalid(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path localPluginJar = Files.writeString(tempDirectory.resolve("local-plugin.jar"), "plugin");
        final PrepareServerMojo.PluginArtifactConfig config = new PrepareServerMojo.PluginArtifactConfig();
        setField(config, "sourceType", "path");
        setField(config, "path", localPluginJar);
        setField(config, "renameTo", "folder/invalid.jar");

        final PrepareServerMojo mojo = new PrepareServerMojo();
        setField(mojo, "plugins", List.of(config));

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(mojo, "resolvePluginArtifactSpecs", new Class<?>[0]))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("may not contain path separators");
    }

    @Test
    void resolvePluginArtifacts_shouldThrowExceptionWhenRepositorySystemIsMissing()
    {
        // setup
        final PluginArtifactSpec spec = new PluginArtifactSpec(
            PluginArtifactSpec.SourceType.MAVEN,
            null,
            "com.example",
            "plugin",
            "1.0.0",
            null,
            "jar",
            false,
            null
        );
        final PrepareServerMojo mojo = new PrepareServerMojo();

        // execute + verify
        assertThatThrownBy(() -> invokePrivate(
            mojo,
            "resolvePluginArtifacts",
            new Class<?>[]{List.class},
            List.of(spec)
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("RepositorySystem");
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokePrivate(
        Object target,
        String methodName,
        Class<?>[] parameterTypes,
        Object @Nullable ... arguments)
        throws Exception
    {
        Method method = getMethod(target, methodName, parameterTypes);
        try
        {
            return (T) method.invoke(target, arguments);
        }
        catch (InvocationTargetException invocationTargetException)
        {
            final Throwable cause = invocationTargetException.getCause();
            if (cause instanceof Exception exception)
                throw exception;
            if (cause instanceof Error error)
                throw error;
            throw invocationTargetException;
        }
    }

    private static Method getMethod(Object target, String methodName, Class<?>[] parameterTypes)
        throws NoSuchMethodException
    {
        Method method = null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
        {
            try
            {
                method = type.getDeclaredMethod(methodName, parameterTypes);
                break;
            }
            catch (NoSuchMethodException ignored)
            {
                // Continue in parent type.
            }
        }
        if (method == null)
        {
            throw new NoSuchMethodException("Method '%s' was not found.".formatted(methodName));
        }

        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable T invokeRecordAccessor(Object target, String methodName)
        throws Exception
    {
        final Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (T) method.invoke(target);
    }

    private static void setField(Object target, String fieldName, Object value)
        throws Exception
    {
        Field field = null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
        {
            try
            {
                field = type.getDeclaredField(fieldName);
                break;
            }
            catch (NoSuchFieldException ignored)
            {
                // Continue in parent type.
            }
        }
        if (field == null)
            throw new NoSuchFieldException("Field '%s' was not found.".formatted(fieldName));
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void configureRequiredFields(PrepareServerMojo mojo, Path tempDirectory, String serverType)
        throws Exception
    {
        setField(mojo, "serverType", serverType);
        setField(mojo, "serverVersion", "latest-supported");
        setField(mojo, "jarCacheDirectoryRoot", tempDirectory.resolve("jars"));
        setField(mojo, "baseServerCacheDirectoryRoot", tempDirectory.resolve("base"));
        setField(mojo, "serverWorkDirectoryRoot", tempDirectory.resolve("work"));
        setField(mojo, "runtimeManifestPath", tempDirectory.resolve("runtime-manifest.json"));
        setField(mojo, "agentSocketDirectory", tempDirectory.resolve("sockets"));
        setField(mojo, "userAgent", "LightKeeper/Test");
        setField(mojo, "serverStartMaxAttempts", 1);
        setField(mojo, "jarCacheExpiryDays", 0);
        setField(mojo, "baseServerCacheExpiryDays", 0);
    }

    private static final class TestPrepareServerMojo extends PrepareServerMojo
    {
        private final PaperDownloadsClient paperDownloadsClient;
        private final SpigotDownloadsClient spigotDownloadsClient;
        private @Nullable ResolvedServerSetup resolvedServerSetup;
        private @Nullable AgentMetadata resolvedAgentMetadata;
        private @Nullable Path resolvedSocketPath;
        private boolean installServerAssetsCalled;
        private @Nullable Path installServerAssetsTargetDirectory;
        private int installServerAssetsPluginCount;

        private TestPrepareServerMojo(
            PaperDownloadsClient paperDownloadsClient,
            SpigotDownloadsClient spigotDownloadsClient)
        {
            this.paperDownloadsClient = paperDownloadsClient;
            this.spigotDownloadsClient = spigotDownloadsClient;
        }

        @Override
        protected PaperDownloadsClient createPaperDownloadsClient(String effectiveUserAgent)
        {
            return paperDownloadsClient;
        }

        @Override
        protected SpigotDownloadsClient createSpigotDownloadsClient(
            PaperDownloadsClient injectedPaperDownloadsClient,
            String effectiveUserAgent)
        {
            return spigotDownloadsClient;
        }

        @Override
        ResolvedServerSetup resolveServerSetup(
            ExecutionContext executionContext,
            AgentMetadata agentMetadata,
            String agentAuthToken,
            String runtimeProtocolVersion)
            throws MojoExecutionException
        {
            if (resolvedServerSetup != null)
                return resolvedServerSetup;
            return super.resolveServerSetup(executionContext, agentMetadata, agentAuthToken, runtimeProtocolVersion);
        }

        @Override
        AgentMetadata resolveAgentMetadata(@Nullable Path path)
            throws MojoExecutionException
        {
            if (resolvedAgentMetadata != null)
                return resolvedAgentMetadata;
            return super.resolveAgentMetadata(path);
        }

        @Override
        Path resolveUdsSocketPath(Path preferredDirectory, String agentAuthToken)
            throws MojoExecutionException
        {
            if (resolvedSocketPath != null)
                return resolvedSocketPath;
            return super.resolveUdsSocketPath(preferredDirectory, agentAuthToken);
        }

        @Override
        void installServerAssets(
            Path targetServerDirectory,
            ExecutionContext executionContext,
            List<PluginArtifactSpec> pluginArtifactSpecs)
            throws MojoExecutionException
        {
            installServerAssetsCalled = true;
            installServerAssetsTargetDirectory = targetServerDirectory;
            installServerAssetsPluginCount = pluginArtifactSpecs.size();
        }

        private void setResolvedServerSetupForTests(ResolvedServerSetup resolvedServerSetup)
        {
            this.resolvedServerSetup = resolvedServerSetup;
        }

        private void setResolvedAgentMetadataForTests(AgentMetadata resolvedAgentMetadata)
        {
            this.resolvedAgentMetadata = resolvedAgentMetadata;
        }

        private void setResolvedSocketPathForTests(Path resolvedSocketPath)
        {
            this.resolvedSocketPath = resolvedSocketPath;
        }

        private boolean installServerAssetsCalled()
        {
            return installServerAssetsCalled;
        }

        private @Nullable Path installServerAssetsTargetDirectory()
        {
            return installServerAssetsTargetDirectory;
        }

        private int installServerAssetsPluginCount()
        {
            return installServerAssetsPluginCount;
        }
    }
}
