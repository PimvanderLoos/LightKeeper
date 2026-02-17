package nl.pim16aap2.lightkeeper.maven;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.maven.provisioning.ServerAssetInstaller;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import nl.pim16aap2.lightkeeper.maven.serverprovider.PaperServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.ServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.SpigotServerProvider;
import nl.pim16aap2.lightkeeper.maven.util.CacheKeyUtil;
import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestWriter;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Mojo(name = "prepare-server", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
@AllArgsConstructor
@NoArgsConstructor
public class PrepareServerMojo extends AbstractMojo
{
    private static final String SERVER_TYPE_PAPER = "paper";
    private static final String SERVER_TYPE_SPIGOT = "spigot";
    private static final int UNIX_SOCKET_PATH_MAX_BYTES = 100;
    private static final Pattern UNRESOLVED_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{[^}]+}");
    private static final List<String> SUPPORTED_SERVER_TYPES = List.of(SERVER_TYPE_PAPER, SERVER_TYPE_SPIGOT);

    @Parameter(property = "lightkeeper.serverType", defaultValue = SERVER_TYPE_PAPER)
    @Nullable
    private String serverType;

    @Parameter(property = "lightkeeper.serverVersion", defaultValue = "latest-supported")
    @Nullable
    private String serverVersion;

    @Parameter(
        property = "lightkeeper.jarCacheDirectoryRoot",
        defaultValue = "${settings.localRepository}/nl/pim16aap2/lightkeeper/cache/jars"
    )
    @Nullable
    private Path jarCacheDirectoryRoot;

    @Parameter(
        property = "lightkeeper.baseServerCacheDirectoryRoot",
        defaultValue = "${settings.localRepository}/nl/pim16aap2/lightkeeper/cache/server"
    )
    @Nullable
    private Path baseServerCacheDirectoryRoot;

    @Parameter(
        property = "lightkeeper.serverWorkDirectoryRoot",
        defaultValue = "${project.build.directory}/lightkeeper-server", required = true
    )
    @Nullable
    private Path serverWorkDirectoryRoot;

    @Parameter(
        property = "lightkeeper.runtimeManifestPath",
        defaultValue = "${project.build.directory}/lightkeeper/runtime-manifest.json", required = true
    )
    @Nullable
    private Path runtimeManifestPath;

    @Parameter(
        property = "lightkeeper.agentSocketDirectory",
        defaultValue = "${project.build.directory}/lightkeeper/sockets", required = true
    )
    @Nullable
    private Path agentSocketDirectory;

    @Parameter(property = "lightkeeper.versionedCacheDirectories", defaultValue = "false")
    private boolean versionedCacheDirectories;

    @Parameter(property = "lightkeeper.jarCacheExpiryDays", defaultValue = "7")
    private int jarCacheExpiryDays;

    @Parameter(property = "lightkeeper.forceRebuildJar", defaultValue = "false")
    private boolean forceRebuildJar;

    @Parameter(property = "lightkeeper.baseServerCacheExpiryDays", defaultValue = "30")
    private int baseServerCacheExpiryDays;

    @Parameter(property = "lightkeeper.forceRecreateBaseServer", defaultValue = "false")
    private boolean forceRecreateBaseServer;

    @Parameter(property = "lightkeeper.cleanupUnusedCacheDirectories", defaultValue = "true")
    private boolean cleanupUnusedCacheDirectories;

    @Parameter(property = "lightkeeper.serverInitTimeoutSeconds", defaultValue = "120")
    private int serverInitTimeoutSeconds;

    @Parameter(property = "lightkeeper.serverStopTimeoutSeconds", defaultValue = "30")
    private int serverStopTimeoutSeconds;

    @Parameter(property = "lightkeeper.serverStartMaxAttempts", defaultValue = "2")
    private int serverStartMaxAttempts;

    @Parameter(property = "lightkeeper.memoryMb", defaultValue = "2048")
    private int memoryMb;

    @Parameter(property = "lightkeeper.javaExecutablePath", defaultValue = "${java.home}/bin/java")
    @Nullable
    private String javaExecutablePath;

    @Parameter(property = "lightkeeper.extraJvmArgs")
    @Nullable
    private String extraJvmArgs;

    @Parameter(property = "lightkeeper.userAgent", required = true)
    @Nullable
    private String userAgent;

    @Parameter(property = "lightkeeper.agentJarPath")
    @Nullable
    private Path agentJarPath;

    @Parameter(property = "lightkeeper.configOverlayPath")
    @Nullable
    private Path configOverlayPath;

    @Parameter
    @Nullable
    private List<WorldInputConfig> worlds;

    @Parameter
    @Nullable
    private List<PluginArtifactConfig> plugins;

    @Component
    @Nullable
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    @Nullable
    private RepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    @Nullable
    private List<RemoteRepository> remoteProjectRepositories;

    /**
     * Resolves/caches the server runtime, installs test assets, and writes the runtime manifest.
     */
    @Override
    public void execute()
        throws MojoExecutionException
    {
        validateConfiguration();
        final ExecutionContext executionContext = buildExecutionContext();
        logPreparationStart(executionContext);
        createRequiredDirectories(executionContext);

        final RuntimePreparation runtimePreparation = prepareRuntimePreparation(executionContext);
        final ServerProvider serverProvider = runtimePreparation.resolvedServerSetup().serverProvider();
        serverProvider.prepareServer();

        final Path targetServerDirectory = serverProvider.targetServerDirectoryPath();
        installServerAssets(targetServerDirectory, executionContext, executionContext.pluginArtifactSpecs());

        final RuntimeManifest runtimeManifest = createRuntimeManifest(
            executionContext.normalizedServerType(),
            runtimePreparation.resolvedServerSetup().manifestServerVersion(),
            runtimePreparation.resolvedServerSetup().manifestBuildId(),
            runtimePreparation.resolvedServerSetup().cacheKey(),
            targetServerDirectory,
            serverProvider,
            runtimePreparation.resolvedServerSetup().memoryMb(),
            runtimePreparation.udsSocketPath(),
            runtimePreparation.agentAuthToken(),
            runtimePreparation.agentMetadata(),
            runtimePreparation.runtimeProtocolVersion(),
            executionContext.worldInputSpecs()
        );
        writeRuntimeManifest(runtimeManifest, executionContext.runtimeManifestPath());
    }

    RuntimePreparation prepareRuntimePreparation(ExecutionContext executionContext)
        throws MojoExecutionException
    {
        final AgentMetadata agentMetadata = resolveAgentMetadata(agentJarPath);
        final String runtimeProtocolVersion = RuntimeProtocol.VERSION;
        final String agentAuthToken = UUID.randomUUID().toString();
        final Path udsSocketPath = resolveUdsSocketPath(executionContext.agentSocketDirectory(), agentAuthToken);
        final ResolvedServerSetup resolvedServerSetup = resolveServerSetup(
            executionContext,
            agentMetadata,
            agentAuthToken,
            runtimeProtocolVersion
        );
        return new RuntimePreparation(
            agentMetadata,
            runtimeProtocolVersion,
            agentAuthToken,
            udsSocketPath,
            resolvedServerSetup
        );
    }

    static void ensureRuntimeManifestParentDirectoryExists(Path runtimeManifestPath)
        throws MojoExecutionException
    {
        final Path runtimeManifestParentDirectory = runtimeManifestPath.getParent();
        if (runtimeManifestParentDirectory == null)
            return;

        FileUtil.createDirectories(runtimeManifestParentDirectory, "runtime manifest directory");
    }

    static void validateExtraJvmArgs(@Nullable String extraJvmArgsValue)
        throws MojoExecutionException
    {
        final @Nullable String normalizedExtraJvmArgs = normalizeOptionalString(extraJvmArgsValue);
        if (normalizedExtraJvmArgs == null)
            return;

        if (UNRESOLVED_PLACEHOLDER_PATTERN.matcher(normalizedExtraJvmArgs).find())
        {
            throw new MojoExecutionException(
                "Configured 'lightkeeper.extraJvmArgs' contains unresolved Maven placeholder(s): '%s'. "
                    .formatted(normalizedExtraJvmArgs)
                    + "Ensure referenced properties are initialized before 'prepare-server' runs."
            );
        }
    }

    ExecutionContext buildExecutionContext()
        throws MojoExecutionException
    {
        return new ExecutionContext(
            normalizeServerType(),
            Objects.requireNonNull(serverVersion),
            Objects.requireNonNull(jarCacheDirectoryRoot),
            Objects.requireNonNull(baseServerCacheDirectoryRoot),
            Objects.requireNonNull(serverWorkDirectoryRoot),
            Objects.requireNonNull(runtimeManifestPath),
            Objects.requireNonNull(agentSocketDirectory),
            Objects.requireNonNull(userAgent),
            resolveWorldInputSpecs(),
            resolvePluginArtifactSpecs()
        );
    }

    private void logPreparationStart(ExecutionContext executionContext)
    {
        getLog().info(
            "Preparing server for platform: '%s' with version: '%s'."
                .formatted(executionContext.normalizedServerType(), executionContext.serverVersion())
        );
    }

    private static void createRequiredDirectories(ExecutionContext executionContext)
        throws MojoExecutionException
    {
        FileUtil.createDirectories(executionContext.jarCacheDirectoryRoot(), "jar cache directory root");
        FileUtil.createDirectories(executionContext.baseServerCacheDirectoryRoot(), "base server cache directory root");
        FileUtil.createDirectories(executionContext.serverWorkDirectoryRoot(), "server work directory root");
        FileUtil.createDirectories(executionContext.agentSocketDirectory(), "agent socket directory");
    }

    ResolvedServerSetup resolveServerSetup(
        ExecutionContext executionContext,
        AgentMetadata agentMetadata,
        String agentAuthToken,
        String runtimeProtocolVersion)
        throws MojoExecutionException
    {
        final PaperDownloadsClient paperDownloadsClient = createPaperDownloadsClient(executionContext.userAgent());

        return switch (executionContext.normalizedServerType())
        {
            case SERVER_TYPE_PAPER -> resolvePaperServerSetup(
                executionContext,
                agentMetadata,
                agentAuthToken,
                runtimeProtocolVersion,
                paperDownloadsClient
            );
            case SERVER_TYPE_SPIGOT -> resolveSpigotServerSetup(
                executionContext,
                agentMetadata,
                agentAuthToken,
                runtimeProtocolVersion,
                paperDownloadsClient
            );
            default -> throw new MojoExecutionException(
                "Unsupported server type '%s'. Supported types: %s"
                    .formatted(executionContext.normalizedServerType(), SUPPORTED_SERVER_TYPES)
            );
        };
    }

    ResolvedServerSetup resolvePaperServerSetup(
        ExecutionContext executionContext,
        AgentMetadata agentMetadata,
        String agentAuthToken,
        String runtimeProtocolVersion,
        PaperDownloadsClient paperDownloadsClient)
        throws MojoExecutionException
    {
        final PaperBuildMetadata paperBuildMetadata =
            paperDownloadsClient.resolveBuild(executionContext.serverVersion());
        final String cacheKey = CacheKeyUtil.createPaperCacheKey(
            paperBuildMetadata.minecraftVersion(),
            paperBuildMetadata.sha256()
        );
        final ServerSpecification serverSpecification = createServerSpecification(
            paperBuildMetadata.minecraftVersion(),
            executionContext.jarCacheDirectoryRoot(),
            executionContext.baseServerCacheDirectoryRoot(),
            executionContext.serverWorkDirectoryRoot(),
            executionContext.runtimeManifestPath(),
            executionContext.agentSocketDirectory(),
            cacheKey,
            executionContext.userAgent(),
            agentMetadata,
            agentAuthToken,
            runtimeProtocolVersion
        );
        return new ResolvedServerSetup(
            new PaperServerProvider(getLog(), serverSpecification, paperBuildMetadata),
            paperBuildMetadata.minecraftVersion(),
            paperBuildMetadata.buildId(),
            cacheKey,
            serverSpecification.memoryMb()
        );
    }

    ResolvedServerSetup resolveSpigotServerSetup(
        ExecutionContext executionContext,
        AgentMetadata agentMetadata,
        String agentAuthToken,
        String runtimeProtocolVersion,
        PaperDownloadsClient paperDownloadsClient)
        throws MojoExecutionException
    {
        final SpigotBuildMetadata spigotBuildMetadata =
            createSpigotDownloadsClient(paperDownloadsClient, executionContext.userAgent())
                .resolveBuild(executionContext.serverVersion());
        final String cacheKey = CacheKeyUtil.createSpigotCacheKey(
            spigotBuildMetadata.minecraftVersion(),
            spigotBuildMetadata.buildToolsIdentity(),
            System.getProperty("java.specification.version"),
            System.getProperty("os.name"),
            System.getProperty("os.arch")
        );
        final ServerSpecification serverSpecification = createServerSpecification(
            spigotBuildMetadata.minecraftVersion(),
            executionContext.jarCacheDirectoryRoot(),
            executionContext.baseServerCacheDirectoryRoot(),
            executionContext.serverWorkDirectoryRoot(),
            executionContext.runtimeManifestPath(),
            executionContext.agentSocketDirectory(),
            cacheKey,
            executionContext.userAgent(),
            agentMetadata,
            agentAuthToken,
            runtimeProtocolVersion
        );
        return new ResolvedServerSetup(
            new SpigotServerProvider(getLog(), serverSpecification, spigotBuildMetadata),
            spigotBuildMetadata.minecraftVersion(),
            0L,
            cacheKey,
            serverSpecification.memoryMb()
        );
    }

    void installServerAssets(
        Path targetServerDirectory,
        ExecutionContext executionContext,
        List<PluginArtifactSpec> pluginArtifactSpecs)
        throws MojoExecutionException
    {
        ServerAssetInstaller.installWorlds(targetServerDirectory, executionContext.worldInputSpecs(), getLog());
        ServerAssetInstaller.installPluginArtifacts(
            targetServerDirectory,
            resolvePluginArtifacts(pluginArtifactSpecs),
            getLog()
        );
        if (configOverlayPath != null)
            ServerAssetInstaller.applyConfigOverlay(configOverlayPath, targetServerDirectory, getLog());
    }

    RuntimeManifest createRuntimeManifest(
        String normalizedServerType,
        String resolvedManifestServerVersion,
        long resolvedManifestBuildId,
        String resolvedCacheKey,
        Path targetServerDirectory,
        ServerProvider serverProvider,
        int resolvedMemoryMb,
        Path udsSocketPath,
        String agentAuthToken,
        AgentMetadata agentMetadata,
        String runtimeProtocolVersion,
        List<WorldInputSpec> worldInputSpecs)
    {
        final List<RuntimeManifest.PreloadedWorld> preloadedWorlds = worldInputSpecs.stream()
            .filter(WorldInputSpec::loadOnStartup)
            .map(worldInput -> new RuntimeManifest.PreloadedWorld(
                worldInput.name(),
                worldInput.environment(),
                worldInput.worldType(),
                worldInput.seed()
            ))
            .toList();

        return new RuntimeManifest(
            normalizedServerType,
            resolvedManifestServerVersion,
            resolvedManifestBuildId,
            resolvedCacheKey,
            targetServerDirectory.toAbsolutePath().toString(),
            serverProvider.targetJarFilePath().toAbsolutePath().toString(),
            resolvedMemoryMb,
            udsSocketPath.toAbsolutePath().toString(),
            agentAuthToken,
            agentJarPath != null ? agentJarPath.toAbsolutePath().toString() : null,
            agentMetadata.sha256(),
            runtimeProtocolVersion,
            agentMetadata.cacheIdentity(),
            normalizeOptionalString(extraJvmArgs),
            preloadedWorlds
        );
    }

    protected PaperDownloadsClient createPaperDownloadsClient(String effectiveUserAgent)
    {
        return new PaperDownloadsClient(getLog(), effectiveUserAgent);
    }

    protected SpigotDownloadsClient createSpigotDownloadsClient(
        PaperDownloadsClient paperDownloadsClient,
        String effectiveUserAgent)
    {
        return new SpigotDownloadsClient(getLog(), paperDownloadsClient, effectiveUserAgent);
    }

    static void writeRuntimeManifest(RuntimeManifest runtimeManifest, Path runtimeManifestPathValue)
        throws MojoExecutionException
    {
        try
        {
            ensureRuntimeManifestParentDirectoryExists(runtimeManifestPathValue);
            new RuntimeManifestWriter().write(runtimeManifest, runtimeManifestPathValue);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to write runtime manifest to '%s'.".formatted(runtimeManifestPathValue),
                exception
            );
        }
    }

    private ServerSpecification createServerSpecification(
        String resolvedServerVersion,
        Path jarCacheDirectoryRootValue,
        Path baseServerCacheDirectoryRootValue,
        Path serverWorkDirectoryRootValue,
        Path runtimeManifestPathValue,
        Path agentSocketDirectoryValue,
        String cacheKey,
        String effectiveUserAgent,
        AgentMetadata agentMetadata,
        String agentAuthToken,
        String runtimeProtocolVersion)
    {
        return new ServerSpecification(
            resolvedServerVersion,
            jarCacheDirectoryRootValue,
            baseServerCacheDirectoryRootValue,
            serverWorkDirectoryRootValue,
            runtimeManifestPathValue,
            agentSocketDirectoryValue,
            versionedCacheDirectories,
            jarCacheExpiryDays,
            forceRebuildJar,
            baseServerCacheExpiryDays,
            forceRecreateBaseServer,
            cleanupUnusedCacheDirectories,
            serverInitTimeoutSeconds,
            serverStopTimeoutSeconds,
            serverStartMaxAttempts,
            memoryMb,
            Objects.requireNonNullElse(javaExecutablePath, "java"),
            extraJvmArgs,
            cacheKey,
            effectiveUserAgent,
            agentJarPath,
            agentMetadata.sha256(),
            agentAuthToken,
            runtimeProtocolVersion,
            agentMetadata.cacheIdentity()
        );
    }

    private String normalizeServerType()
    {
        return Objects.requireNonNull(serverType, "serverType may not be null.")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    void validateConfiguration()
        throws MojoExecutionException
    {
        final String normalizedType = normalizeServerType();
        if (!SUPPORTED_SERVER_TYPES.contains(normalizedType))
        {
            throw new MojoExecutionException(
                "Unsupported server type '%s'. Supported types: %s"
                    .formatted(serverType, SUPPORTED_SERVER_TYPES)
            );
        }

        if (userAgent == null || userAgent.isBlank())
            throw new MojoExecutionException("A non-empty `lightkeeper.userAgent` value is required.");

        if (serverStartMaxAttempts < 1)
            throw new MojoExecutionException("`lightkeeper.serverStartMaxAttempts` must be at least 1.");

        if (jarCacheExpiryDays < 0)
            throw new MojoExecutionException("`lightkeeper.jarCacheExpiryDays` must be at least 0.");

        if (baseServerCacheExpiryDays < 0)
            throw new MojoExecutionException("`lightkeeper.baseServerCacheExpiryDays` must be at least 0.");

        validateExtraJvmArgs(extraJvmArgs);
    }

    private List<WorldInputSpec> resolveWorldInputSpecs()
        throws MojoExecutionException
    {
        final List<WorldInputSpec> inputSpecs = new ArrayList<>();
        final List<WorldInputConfig> configuredWorlds = worlds == null ? List.of() : worlds;
        for (final WorldInputConfig worldInputConfig : configuredWorlds)
        {
            if (worldInputConfig == null)
                throw new MojoExecutionException("Configured world entry may not be null.");

            final String worldName = validateWorldName(worldInputConfig.name);
            final WorldInputSpec.SourceType sourceType = parseWorldSourceType(worldInputConfig.sourceType);
            if (worldInputConfig.sourcePath == null)
            {
                throw new MojoExecutionException(
                    "Missing required configuration value 'lightkeeper.worlds.sourcePath'."
                );
            }
            final Path sourcePath = worldInputConfig.sourcePath.toAbsolutePath().normalize();

            if (sourceType == WorldInputSpec.SourceType.FOLDER && !Files.isDirectory(sourcePath))
            {
                throw new MojoExecutionException(
                    "Configured world '%s' requires a directory sourcePath, but '%s' is not a directory."
                        .formatted(worldName, sourcePath)
                );
            }
            if (sourceType == WorldInputSpec.SourceType.ARCHIVE && !Files.isRegularFile(sourcePath))
            {
                throw new MojoExecutionException(
                    "Configured world '%s' requires a file archive sourcePath, but '%s' is not a regular file."
                        .formatted(worldName, sourcePath)
                );
            }

            inputSpecs.add(new WorldInputSpec(
                worldName,
                sourceType,
                sourcePath,
                worldInputConfig.overwrite == null || worldInputConfig.overwrite,
                worldInputConfig.loadOnStartup == null || worldInputConfig.loadOnStartup,
                normalizeWorldEnvironment(worldInputConfig.environment),
                normalizeWorldType(worldInputConfig.worldType),
                worldInputConfig.seed == null ? 0L : worldInputConfig.seed
            ));
        }

        return inputSpecs;
    }

    private List<PluginArtifactSpec> resolvePluginArtifactSpecs()
        throws MojoExecutionException
    {
        final List<PluginArtifactSpec> specs = new ArrayList<>();
        final List<PluginArtifactConfig> configuredPlugins = plugins == null ? List.of() : plugins;
        for (final PluginArtifactConfig pluginArtifactConfig : configuredPlugins)
        {
            if (pluginArtifactConfig == null)
                throw new MojoExecutionException("Configured plugin entry may not be null.");

            final PluginArtifactSpec.SourceType sourceType = parsePluginSourceType(pluginArtifactConfig.sourceType);
            final String renameTo = normalizeOptionalPluginFileName(pluginArtifactConfig.renameTo);

            if (sourceType == PluginArtifactSpec.SourceType.PATH)
            {
                if (pluginArtifactConfig.path == null)
                    throw new MojoExecutionException(
                        "Missing required configuration value 'lightkeeper.plugins.path'."
                    );
                final Path path = pluginArtifactConfig.path.toAbsolutePath().normalize();
                if (!Files.isRegularFile(path))
                {
                    throw new MojoExecutionException(
                        "Configured plugin path source '%s' does not exist as a regular file."
                            .formatted(path)
                    );
                }

                specs.add(new PluginArtifactSpec(
                    sourceType,
                    path,
                    null,
                    null,
                    null,
                    null,
                    "jar",
                    false,
                    renameTo
                ));
                continue;
            }

            final String groupId = requireNonBlank(pluginArtifactConfig.groupId, "lightkeeper.plugins.groupId");
            final String artifactId = requireNonBlank(
                pluginArtifactConfig.artifactId,
                "lightkeeper.plugins.artifactId"
            );
            final String version = requireNonBlank(pluginArtifactConfig.version, "lightkeeper.plugins.version");
            final String classifier = normalizeOptionalString(pluginArtifactConfig.classifier);
            final @Nullable String configuredType = normalizeOptionalString(pluginArtifactConfig.type);
            final String type = configuredType == null ? "jar" : configuredType;
            final boolean includeTransitive = pluginArtifactConfig.includeTransitive != null &&
                pluginArtifactConfig.includeTransitive;

            if (includeTransitive && renameTo != null)
            {
                throw new MojoExecutionException(
                    "Configured plugin '%s:%s:%s' cannot set renameTo when includeTransitive=true."
                        .formatted(groupId, artifactId, version)
                );
            }

            specs.add(new PluginArtifactSpec(
                sourceType,
                null,
                groupId,
                artifactId,
                version,
                classifier,
                type,
                includeTransitive,
                renameTo
            ));
        }

        return specs;
    }

    List<ResolvedPluginArtifact> resolvePluginArtifacts(List<PluginArtifactSpec> specs)
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

            resolvedPluginArtifacts.addAll(resolveMavenPluginArtifacts(spec));
        }

        return resolvedPluginArtifacts;
    }

    private List<ResolvedPluginArtifact> resolveMavenPluginArtifacts(PluginArtifactSpec spec)
        throws MojoExecutionException
    {
        final RepositorySystem resolver = Objects.requireNonNull(repositorySystem,
            "Maven RepositorySystem was not injected by the plugin runtime.");
        final RepositorySystemSession session = Objects.requireNonNull(repositorySystemSession,
            "Maven RepositorySystemSession was not injected by the plugin runtime.");
        final List<RemoteRepository> repositories = Objects.requireNonNullElse(remoteProjectRepositories, List.of());

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
                .setRepositories(repositories);
            try
            {
                final ArtifactResult result = resolver.resolveArtifact(session, request);
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
        collectRequest.setRepositories(repositories);

        final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
        try
        {
            final DependencyResult dependencyResult = resolver.resolveDependencies(session, dependencyRequest);
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

    AgentMetadata resolveAgentMetadata(@Nullable Path path)
        throws MojoExecutionException
    {
        if (path == null)
            return new AgentMetadata(null, "no-agent");

        if (Files.notExists(path) || !Files.isRegularFile(path))
            throw new MojoExecutionException("Configured agent jar path '%s' does not exist.".formatted(path));

        final String sha256 = HashUtil.sha256(path);
        final String fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        return new AgentMetadata(sha256, fileName + ":" + sha256);
    }

    Path resolveUdsSocketPath(Path preferredDirectory, String agentAuthToken)
        throws MojoExecutionException
    {
        final String socketFileName = "lk-%s.sock".formatted(agentAuthToken.substring(0, 8));
        final Path preferredPath = preferredDirectory.resolve(socketFileName).toAbsolutePath();
        if (fitsUnixSocketPath(preferredPath))
            return preferredPath;

        final Path fallbackDirectory = Path.of(System.getProperty("java.io.tmpdir"), "lightkeeper-sockets");
        FileUtil.createDirectories(fallbackDirectory, "fallback agent socket directory");

        final Path fallbackPath = fallbackDirectory.resolve(socketFileName).toAbsolutePath();
        if (fitsUnixSocketPath(fallbackPath))
        {
            getLog().warn(
                "Configured socket path is too long for AF_UNIX. Falling back to short path '%s'."
                    .formatted(fallbackPath)
            );
            return fallbackPath;
        }

        throw new MojoExecutionException(
            "Unable to generate a valid AF_UNIX socket path. Tried '%s' and '%s'."
                .formatted(preferredPath, fallbackPath)
        );
    }

    private static boolean fitsUnixSocketPath(Path path)
    {
        return path.toString().getBytes(StandardCharsets.UTF_8).length <= UNIX_SOCKET_PATH_MAX_BYTES;
    }

    private static WorldInputSpec.SourceType parseWorldSourceType(@Nullable String sourceType)
        throws MojoExecutionException
    {
        final String normalized = requireNonBlank(sourceType, "lightkeeper.worlds.sourceType");
        try
        {
            return WorldInputSpec.SourceType.valueOf(normalized.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception)
        {
            throw new MojoExecutionException(
                "Unsupported world sourceType '%s'. Supported values: %s"
                    .formatted(sourceType, List.of(WorldInputSpec.SourceType.values())),
                exception
            );
        }
    }

    private static PluginArtifactSpec.SourceType parsePluginSourceType(@Nullable String sourceType)
        throws MojoExecutionException
    {
        final String normalized = requireNonBlank(sourceType, "lightkeeper.plugins.sourceType");
        try
        {
            return PluginArtifactSpec.SourceType.valueOf(normalized.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception)
        {
            throw new MojoExecutionException(
                "Unsupported plugin sourceType '%s'. Supported values: %s"
                    .formatted(sourceType, List.of(PluginArtifactSpec.SourceType.values())),
                exception
            );
        }
    }

    private static String validateWorldName(@Nullable String worldName)
        throws MojoExecutionException
    {
        final String name = requireNonBlank(worldName, "lightkeeper.worlds.name");
        if (name.contains("/") || name.contains("\\") || name.equals(".") || name.contains(".."))
        {
            throw new MojoExecutionException(
                "Invalid world name '%s'. World names may not contain path separators, '.', or '..'."
                    .formatted(name)
            );
        }
        return name;
    }

    private static String normalizeWorldEnvironment(@Nullable String environment)
        throws MojoExecutionException
    {
        final String normalized = normalizeOptionalString(environment);
        if (normalized == null)
            return "NORMAL";

        final String upperCase = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("NORMAL", "NETHER", "THE_END").contains(upperCase))
        {
            throw new MojoExecutionException(
                "Unsupported world environment '%s'. Supported values: NORMAL, NETHER, THE_END."
                    .formatted(environment)
            );
        }
        return upperCase;
    }

    private static String normalizeWorldType(@Nullable String worldType)
        throws MojoExecutionException
    {
        final String normalized = normalizeOptionalString(worldType);
        if (normalized == null)
            return "NORMAL";

        final String upperCase = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("NORMAL", "FLAT").contains(upperCase))
        {
            throw new MojoExecutionException(
                "Unsupported worldType '%s'. Supported values: NORMAL, FLAT."
                    .formatted(worldType)
            );
        }
        return upperCase;
    }

    private static @Nullable String normalizeOptionalPluginFileName(@Nullable String fileName)
        throws MojoExecutionException
    {
        final String normalized = normalizeOptionalString(fileName);
        if (normalized == null)
            return null;
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".jar"))
            throw new MojoExecutionException("Configured renameTo '%s' must end with .jar.".formatted(normalized));
        if (normalized.contains("/") || normalized.contains("\\"))
        {
            throw new MojoExecutionException(
                "Configured renameTo '%s' may not contain path separators.".formatted(normalized)
            );
        }
        return normalized;
    }

    private static String requireNonBlank(@Nullable String value, String fieldName)
        throws MojoExecutionException
    {
        final String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty())
            throw new MojoExecutionException("Missing required configuration value '%s'.".formatted(fieldName));
        return trimmed;
    }

    private static @Nullable String normalizeOptionalString(@Nullable String value)
    {
        if (value == null)
            return null;
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    record AgentMetadata(@Nullable String sha256, String cacheIdentity)
    {
    }

    record ExecutionContext(
        String normalizedServerType,
        String serverVersion,
        Path jarCacheDirectoryRoot,
        Path baseServerCacheDirectoryRoot,
        Path serverWorkDirectoryRoot,
        Path runtimeManifestPath,
        Path agentSocketDirectory,
        String userAgent,
        List<WorldInputSpec> worldInputSpecs,
        List<PluginArtifactSpec> pluginArtifactSpecs
    )
    {
    }

    record ResolvedServerSetup(
        ServerProvider serverProvider,
        String manifestServerVersion,
        long manifestBuildId,
        String cacheKey,
        int memoryMb
    )
    {
    }

    record RuntimePreparation(
        AgentMetadata agentMetadata,
        String runtimeProtocolVersion,
        String agentAuthToken,
        Path udsSocketPath,
        ResolvedServerSetup resolvedServerSetup
    )
    {
    }

    public static final class WorldInputConfig
    {
        @Parameter(required = true)
        @Nullable
        private String name;

        @Parameter(required = true)
        @Nullable
        private String sourceType;

        @Parameter(required = true)
        @Nullable
        private Path sourcePath;

        @Parameter
        @Nullable
        private Boolean overwrite;

        @Parameter
        @Nullable
        private Boolean loadOnStartup;

        @Parameter
        @Nullable
        private String environment;

        @Parameter
        @Nullable
        private String worldType;

        @Parameter
        @Nullable
        private Long seed;
    }

    public static final class PluginArtifactConfig
    {
        @Parameter(required = true)
        @Nullable
        private String sourceType;

        @Parameter
        @Nullable
        private Path path;

        @Parameter
        @Nullable
        private String groupId;

        @Parameter
        @Nullable
        private String artifactId;

        @Parameter
        @Nullable
        private String version;

        @Parameter
        @Nullable
        private String classifier;

        @Parameter
        @Nullable
        private String type;

        @Parameter
        @Nullable
        private Boolean includeTransitive;

        @Parameter
        @Nullable
        private String renameTo;
    }
}
