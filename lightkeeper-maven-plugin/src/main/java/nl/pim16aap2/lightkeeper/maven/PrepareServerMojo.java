package nl.pim16aap2.lightkeeper.maven;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestWriter;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import nl.pim16aap2.lightkeeper.maven.serverprovider.PaperServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.ServerProvider;
import nl.pim16aap2.lightkeeper.maven.util.CacheKeyUtil;
import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Mojo(name = "prepare-server", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
@AllArgsConstructor
@NoArgsConstructor
public class PrepareServerMojo extends AbstractMojo
{
    private static final String SERVER_TYPE_PAPER = "paper";
    private static final int UNIX_SOCKET_PATH_MAX_BYTES = 100;
    private static final List<String> SUPPORTED_SERVER_TYPES = List.of(SERVER_TYPE_PAPER);

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

    @Override
    public void execute()
        throws MojoExecutionException
    {
        validateConfiguration();

        final String effectiveServerVersion = Objects.requireNonNull(serverVersion);
        final Path effectiveJarCacheDirectoryRoot = Objects.requireNonNull(jarCacheDirectoryRoot);
        final Path effectiveBaseServerCacheDirectoryRoot = Objects.requireNonNull(baseServerCacheDirectoryRoot);
        final Path effectiveServerWorkDirectoryRoot = Objects.requireNonNull(serverWorkDirectoryRoot);
        final Path effectiveRuntimeManifestPath = Objects.requireNonNull(runtimeManifestPath);
        final Path effectiveAgentSocketDirectory = Objects.requireNonNull(agentSocketDirectory);

        getLog().info(
            "Preparing server for platform: '" + serverType + "' with version: '" + effectiveServerVersion + "'."
        );

        FileUtil.createDirectories(effectiveJarCacheDirectoryRoot, "jar cache directory root");
        FileUtil.createDirectories(effectiveBaseServerCacheDirectoryRoot, "base server cache directory root");
        FileUtil.createDirectories(effectiveServerWorkDirectoryRoot, "server work directory root");
        FileUtil.createDirectories(effectiveAgentSocketDirectory, "agent socket directory");

        final String effectiveUserAgent = Objects.requireNonNull(userAgent);
        final PaperBuildMetadata paperBuildMetadata =
            new PaperDownloadsClient(getLog(), effectiveUserAgent).resolveBuild(effectiveServerVersion);

        final AgentMetadata agentMetadata = resolveAgentMetadata(agentJarPath);
        final String cacheKey = CacheKeyUtil.createCacheKey(List.of(
            SERVER_TYPE_PAPER,
            paperBuildMetadata.minecraftVersion(),
            Long.toString(paperBuildMetadata.buildId()),
            System.getProperty("java.specification.version"),
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            RuntimeProtocol.VERSION,
            agentMetadata.cacheIdentity()
        ));

        final String agentAuthToken = UUID.randomUUID().toString();
        final Path udsSocketPath = resolveUdsSocketPath(effectiveAgentSocketDirectory, agentAuthToken);

        final ServerSpecification serverSpecification = new ServerSpecification(
            paperBuildMetadata.minecraftVersion(),
            effectiveJarCacheDirectoryRoot,
            effectiveBaseServerCacheDirectoryRoot,
            effectiveServerWorkDirectoryRoot,
            effectiveRuntimeManifestPath,
            effectiveAgentSocketDirectory,
            versionedCacheDirectories,
            jarCacheExpiryDays,
            forceRebuildJar,
            baseServerCacheExpiryDays,
            forceRecreateBaseServer,
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
            RuntimeProtocol.VERSION,
            agentMetadata.cacheIdentity()
        );

        final ServerProvider serverProvider = new PaperServerProvider(getLog(), serverSpecification, paperBuildMetadata);

        serverProvider.prepareServer();

        final RuntimeManifest runtimeManifest = new RuntimeManifest(
            SERVER_TYPE_PAPER,
            paperBuildMetadata.minecraftVersion(),
            paperBuildMetadata.buildId(),
            cacheKey,
            serverProvider.targetServerDirectoryPath().toAbsolutePath().toString(),
            serverProvider.targetJarFilePath().toAbsolutePath().toString(),
            udsSocketPath.toAbsolutePath().toString(),
            agentAuthToken,
            agentJarPath != null ? agentJarPath.toAbsolutePath().toString() : null,
            agentMetadata.sha256(),
            RuntimeProtocol.VERSION,
            agentMetadata.cacheIdentity()
        );
        try
        {
            new RuntimeManifestWriter().write(runtimeManifest, effectiveRuntimeManifestPath);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to write runtime manifest to '%s'.".formatted(effectiveRuntimeManifestPath),
                exception
            );
        }
    }

    private void validateConfiguration()
        throws MojoExecutionException
    {
        final String normalizedType = Objects.requireNonNull(serverType).toLowerCase(Locale.ROOT);
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
    }

    private AgentMetadata resolveAgentMetadata(@Nullable Path path)
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

    private Path resolveUdsSocketPath(Path preferredDirectory, String agentAuthToken)
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

    private record AgentMetadata(@Nullable String sha256, String cacheIdentity)
    {
    }
}
