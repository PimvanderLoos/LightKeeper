package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.maven.provisioning.ServerAssetInstaller;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import nl.pim16aap2.lightkeeper.maven.LightkeeperEmbeddedAgent;
import nl.pim16aap2.lightkeeper.maven.PaperDownloadsClient;
import nl.pim16aap2.lightkeeper.maven.PaperBuildMetadata;
import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import nl.pim16aap2.lightkeeper.maven.SpigotBuildMetadata;
import nl.pim16aap2.lightkeeper.maven.SpigotDownloadsClient;
import nl.pim16aap2.lightkeeper.maven.serverprovider.PaperServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.ServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.SpigotServerProvider;
import nl.pim16aap2.lightkeeper.maven.util.CacheKeyUtil;
import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves/caches a Minecraft test server installation and writes a runtime manifest for integration tests.
 */
@Mojo(name = "prepare-server", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
@AllArgsConstructor
@NoArgsConstructor
public class PrepareServerMojo extends AbstractMojo
{
    private static final String SERVER_TYPE_PAPER = "paper";
    private static final String SERVER_TYPE_SPIGOT = "spigot";
    private static final List<String> SUPPORTED_SERVER_TYPES = List.of(SERVER_TYPE_PAPER, SERVER_TYPE_SPIGOT);

    /**
     * Skips server preparation entirely when set. Shared with the cleanup goal so a single
     * {@code -Dlightkeeper.skip=true} disables a whole LightKeeper lane, e.g. from a CI matrix.
     * <p>
     * Note: skipping preparation without also skipping the integration tests (e.g. {@code -DskipITs}) leaves
     * failsafe without a runtime manifest, so the tests will fail to start.
     */
    @Parameter(property = "lightkeeper.skip", defaultValue = "false")
    private boolean skip;

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
        property = "lightkeeper.pluginArtifactCacheDirectoryRoot",
        defaultValue = "${settings.localRepository}/nl/pim16aap2/lightkeeper/cache/plugins"
    )
    @Nullable
    private Path pluginArtifactCacheDirectoryRoot;

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

    /**
     * Optional directory for the agent's UDS socket file.
     *
     * <p>When not configured, a short per-user runtime directory is used to stay within the AF_UNIX path length
     * limit. Explicitly configured paths that exceed that limit fail the build.</p>
     */
    @Parameter(property = "lightkeeper.agentSocketDirectory")
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

    /**
     * Deprecated legacy parameter kept only for fail-fast validation.
     *
     * <p>Agent provisioning is now internal and automatic; callers must not set this value.</p>
     */
    @Parameter(property = "lightkeeper.agentJarPath")
    @Nullable
    private Path agentJarPath;

    @Parameter(property = "lightkeeper.configOverlayPath")
    @Nullable
    private Path configOverlayPath;

    @Parameter
    @Nullable
    private List<PrepareServerWorldInputConfig> worlds;

    @Parameter
    @Nullable
    private List<PrepareServerPluginArtifactConfig> plugins;

    @Component
    @Nullable
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    @Nullable
    private RepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    @Nullable
    private List<RemoteRepository> remoteProjectRepositories;

    @Override
    public void execute()
        throws MojoExecutionException
    {
        if (skip)
        {
            getLog().info("Skipping server preparation because 'lightkeeper.skip' is set.");
            deleteStaleRuntimeManifest();
            return;
        }

        validateConfiguration();
        final PrepareServerExecutionContext executionContext = buildExecutionContext();
        logPreparationStart(executionContext);
        createRequiredDirectories(executionContext);

        final PrepareServerRuntimePreparation runtimePreparation = prepareRuntimePreparation(executionContext);
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

    /**
     * Deletes a runtime manifest left behind by a previous run.
     * <p>
     * Without this, skipping preparation in a non-clean workspace would leave integration tests silently running
     * against the stale manifest instead of failing fast on a missing one.
     *
     * @throws MojoExecutionException
     *     When a stale manifest exists but cannot be deleted.
     */
    private void deleteStaleRuntimeManifest()
        throws MojoExecutionException
    {
        if (runtimeManifestPath == null)
            return;
        try
        {
            if (Files.deleteIfExists(runtimeManifestPath))
                getLog().info("Deleted stale runtime manifest '%s'.".formatted(runtimeManifestPath));
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to delete stale runtime manifest '%s'.".formatted(runtimeManifestPath), exception);
        }
    }

    PrepareServerRuntimePreparation prepareRuntimePreparation(PrepareServerExecutionContext executionContext)
        throws MojoExecutionException
    {
        final PrepareServerAgentMetadata agentMetadata = resolveAgentMetadata();
        final int runtimeProtocolVersion = RuntimeProtocol.VERSION;
        final String agentAuthToken = UUID.randomUUID().toString();
        final Path udsSocketPath =
            resolveUdsSocketPath(executionContext.configuredAgentSocketDirectory(), agentAuthToken);
        final PrepareServerResolvedServerSetup resolvedServerSetup = resolveServerSetup(
            executionContext,
            agentMetadata,
            agentAuthToken,
            runtimeProtocolVersion
        );
        return new PrepareServerRuntimePreparation(
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
        PrepareServerRuntimeSupport.ensureRuntimeManifestParentDirectoryExists(runtimeManifestPath);
    }

    static void validateExtraJvmArgs(@Nullable String extraJvmArgsValue)
        throws MojoExecutionException
    {
        PrepareServerRuntimeSupport.validateExtraJvmArgs(extraJvmArgsValue);
    }

    PrepareServerExecutionContext buildExecutionContext()
        throws MojoExecutionException
    {
        return new PrepareServerExecutionContext(
            normalizeServerType(),
            Objects.requireNonNull(serverVersion),
            Objects.requireNonNull(jarCacheDirectoryRoot),
            Objects.requireNonNull(baseServerCacheDirectoryRoot),
            pluginArtifactCacheDirectoryRoot == null
                ? Path.of(System.getProperty("java.io.tmpdir"), "lightkeeper-plugin-cache")
                : pluginArtifactCacheDirectoryRoot,
            Objects.requireNonNull(serverWorkDirectoryRoot),
            Objects.requireNonNull(runtimeManifestPath),
            agentSocketDirectory,
            Objects.requireNonNull(userAgent),
            resolveWorldInputSpecs(),
            resolvePluginArtifactSpecs()
        );
    }

    private void logPreparationStart(PrepareServerExecutionContext executionContext)
    {
        getLog().info(
            "Preparing server for platform: '%s' with version: '%s'."
                .formatted(executionContext.normalizedServerType(), executionContext.serverVersion())
        );
    }

    private static void createRequiredDirectories(PrepareServerExecutionContext executionContext)
        throws MojoExecutionException
    {
        FileUtil.createDirectories(executionContext.jarCacheDirectoryRoot(), "jar cache directory root");
        FileUtil.createDirectories(executionContext.baseServerCacheDirectoryRoot(), "base server cache directory root");
        FileUtil.createDirectories(
            executionContext.pluginArtifactCacheDirectoryRoot(),
            "plugin artifact cache directory root"
        );
        FileUtil.createDirectories(executionContext.serverWorkDirectoryRoot(), "server work directory root");
    }

    PrepareServerResolvedServerSetup resolveServerSetup(
        PrepareServerExecutionContext executionContext,
        PrepareServerAgentMetadata agentMetadata,
        String agentAuthToken,
        int runtimeProtocolVersion)
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
                runtimeProtocolVersion
            );
            default -> throw new MojoExecutionException(
                "Unsupported server type '%s'. Supported types: %s"
                    .formatted(executionContext.normalizedServerType(), SUPPORTED_SERVER_TYPES)
            );
        };
    }

    PrepareServerResolvedServerSetup resolvePaperServerSetup(
        PrepareServerExecutionContext executionContext,
        PrepareServerAgentMetadata agentMetadata,
        String agentAuthToken,
        int runtimeProtocolVersion,
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
            cacheKey,
            executionContext.userAgent(),
            agentMetadata,
            agentAuthToken,
            runtimeProtocolVersion
        );
        return new PrepareServerResolvedServerSetup(
            new PaperServerProvider(getLog(), serverSpecification, paperBuildMetadata),
            paperBuildMetadata.minecraftVersion(),
            paperBuildMetadata.buildId(),
            cacheKey,
            serverSpecification.memoryMb()
        );
    }

    PrepareServerResolvedServerSetup resolveSpigotServerSetup(
        PrepareServerExecutionContext executionContext,
        PrepareServerAgentMetadata agentMetadata,
        String agentAuthToken,
        int runtimeProtocolVersion)
        throws MojoExecutionException
    {
        final SpigotBuildMetadata spigotBuildMetadata =
            createSpigotDownloadsClient(executionContext.userAgent())
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
            cacheKey,
            executionContext.userAgent(),
            agentMetadata,
            agentAuthToken,
            runtimeProtocolVersion
        );
        return new PrepareServerResolvedServerSetup(
            new SpigotServerProvider(getLog(), serverSpecification, spigotBuildMetadata),
            spigotBuildMetadata.minecraftVersion(),
            0L,
            cacheKey,
            serverSpecification.memoryMb()
        );
    }

    void installServerAssets(
        Path targetServerDirectory,
        PrepareServerExecutionContext executionContext,
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
        PrepareServerAgentMetadata agentMetadata,
        int runtimeProtocolVersion,
        List<WorldInputSpec> worldInputSpecs)
    {
        final List<RuntimeManifest.ProvisionedWorld> provisionedWorlds = worldInputSpecs.stream()
            .map(worldInput -> new RuntimeManifest.ProvisionedWorld(
                worldInput.name(),
                worldInput.environment(),
                worldInput.worldType(),
                worldInput.seed(),
                worldInput.loadOnStartup()
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
            targetServerDirectory.resolve("plugins").resolve(LightkeeperEmbeddedAgent.FILE_NAME).toAbsolutePath()
                .toString(),
            agentMetadata.sha256(),
            runtimeProtocolVersion,
            agentMetadata.cacheIdentity(),
            PrepareServerInputResolver.normalizeOptionalString(extraJvmArgs),
            provisionedWorlds
        );
    }

    protected PaperDownloadsClient createPaperDownloadsClient(String effectiveUserAgent)
    {
        return new PaperDownloadsClient(getLog(), effectiveUserAgent);
    }

    protected SpigotDownloadsClient createSpigotDownloadsClient(String effectiveUserAgent)
    {
        return new SpigotDownloadsClient(getLog(), effectiveUserAgent);
    }

    static void writeRuntimeManifest(RuntimeManifest runtimeManifest, Path runtimeManifestPathValue)
        throws MojoExecutionException
    {
        PrepareServerRuntimeSupport.writeRuntimeManifest(runtimeManifest, runtimeManifestPathValue);
    }

    private ServerSpecification createServerSpecification(
        String resolvedServerVersion,
        Path jarCacheDirectoryRootValue,
        Path baseServerCacheDirectoryRootValue,
        Path serverWorkDirectoryRootValue,
        Path runtimeManifestPathValue,
        String cacheKey,
        String effectiveUserAgent,
        PrepareServerAgentMetadata agentMetadata,
        String agentAuthToken,
        int runtimeProtocolVersion)
    {
        return new ServerSpecification(
            resolvedServerVersion,
            jarCacheDirectoryRootValue,
            baseServerCacheDirectoryRootValue,
            serverWorkDirectoryRootValue,
            runtimeManifestPathValue,
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
            agentMetadata.sha256(),
            agentAuthToken,
            runtimeProtocolVersion,
            agentMetadata.cacheIdentity()
        );
    }

    private String normalizeServerType()
    {
        return configurationValidator().normalizeServerType(serverType);
    }

    void validateConfiguration()
        throws MojoExecutionException
    {
        configurationValidator().validateConfiguration(
            serverType,
            userAgent,
            agentJarPath,
            serverStartMaxAttempts,
            jarCacheExpiryDays,
            baseServerCacheExpiryDays,
            extraJvmArgs
        );
    }

    private List<WorldInputSpec> resolveWorldInputSpecs()
        throws MojoExecutionException
    {
        return inputResolver().resolveWorldInputSpecs(worlds);
    }

    private List<PluginArtifactSpec> resolvePluginArtifactSpecs()
        throws MojoExecutionException
    {
        return inputResolver().resolvePluginArtifactSpecs(plugins);
    }

    List<ResolvedPluginArtifact> resolvePluginArtifacts(List<PluginArtifactSpec> specs)
        throws MojoExecutionException
    {
        final RepositorySystem resolver = Objects.requireNonNull(repositorySystem,
            "Maven RepositorySystem was not injected by the plugin runtime.");
        final RepositorySystemSession session = Objects.requireNonNull(repositorySystemSession,
            "Maven RepositorySystemSession was not injected by the plugin runtime.");
        final List<RemoteRepository> repositories = Objects.requireNonNullElse(remoteProjectRepositories, List.of());
        return pluginArtifactResolver().resolvePluginArtifacts(
            specs,
            resolver,
            session,
            repositories,
            pluginArtifactCacheDirectoryRoot == null
                ? Path.of(System.getProperty("java.io.tmpdir"), "lightkeeper-plugin-cache")
                : pluginArtifactCacheDirectoryRoot,
            Objects.requireNonNullElse(userAgent, "LightKeeper/Test"),
            getLog()
        );
    }

    PrepareServerAgentMetadata resolveAgentMetadata()
        throws MojoExecutionException
    {
        return runtimeSupport().resolveAgentMetadata();
    }

    Path resolveUdsSocketPath(@Nullable Path configuredDirectory, String agentAuthToken)
        throws MojoExecutionException
    {
        return runtimeSupport().resolveUdsSocketPath(configuredDirectory, agentAuthToken);
    }

    private PrepareServerConfigurationValidator configurationValidator()
    {
        return new PrepareServerConfigurationValidator(SUPPORTED_SERVER_TYPES);
    }

    private PrepareServerInputResolver inputResolver()
    {
        return new PrepareServerInputResolver();
    }

    private PrepareServerRuntimeSupport runtimeSupport()
    {
        return new PrepareServerRuntimeSupport(getLog());
    }

    private PrepareServerPluginArtifactResolver pluginArtifactResolver()
    {
        return new PrepareServerPluginArtifactResolver();
    }
}
