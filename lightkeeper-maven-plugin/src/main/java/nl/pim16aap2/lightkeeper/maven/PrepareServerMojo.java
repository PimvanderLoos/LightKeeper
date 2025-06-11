package nl.pim16aap2.lightkeeper.maven;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import nl.pim16aap2.lightkeeper.maven.serverprovider.PaperServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.ServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.SpigotServerProvider;
import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Mojo(name = "prepare-server", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
@AllArgsConstructor
@NoArgsConstructor
public class PrepareServerMojo extends AbstractMojo
{
    private static final List<String> SUPPORTED_SERVER_TYPES = List.of("spigot", "paper");

    @Parameter(property = "lightkeeper.serverType", defaultValue = "spigot")
    private String serverType;

    @Parameter(property = "lightkeeper.serverVersion", defaultValue = "1.21.5")
    private String serverVersion;

    @Parameter(
        property = "lightkeeper.jarCacheDirectoryRoot",
        defaultValue = "${settings.localRepository}/nl/pim16aap2/lightkeeper/cache/jars"
    )
    private Path jarCacheDirectoryRoot;

    @Parameter(
        property = "lightkeeper.baseServerCacheDirectoryRoot",
        defaultValue = "${settings.localRepository}/nl/pim16aap2/lightkeeper/cache/server"
    )
    private Path baseServerCacheDirectoryRoot;

    @Parameter(
        property = "lightkeeper.serverWorkDirectoryRoot",
        defaultValue = "${project.build.directory}/lightkeeper-server", required = true
    )
    private Path serverWorkDirectoryRoot;

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

    @Parameter(property = "lightkeeper.memoryMb", defaultValue = "2048")
    private int memoryMb;

    @Parameter(property = "lightkeeper.javaExecutablePath", defaultValue = "${java.home}/bin/java")
    @Nullable
    private String javaExecutablePath;

    @Parameter(property = "lightkeeper.extraJvmArgs")
    @Nullable
    private String extraJvmArgs;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute()
        throws MojoExecutionException
    {
        getLog().info("Preparing server for platform: '" + serverType + "' with version: '" + serverVersion + "'.");

        FileUtil.createDirectories(jarCacheDirectoryRoot, "jar cache directory root");
        FileUtil.createDirectories(baseServerCacheDirectoryRoot, "base server cache directory root");
        FileUtil.createDirectories(serverWorkDirectoryRoot, "server work directory root");

        final ServerSpecification serverSpecification = new ServerSpecification(
            serverVersion,
            jarCacheDirectoryRoot,
            baseServerCacheDirectoryRoot,
            serverWorkDirectoryRoot,
            versionedCacheDirectories,
            jarCacheExpiryDays,
            forceRebuildJar,
            baseServerCacheExpiryDays,
            forceRecreateBaseServer,
            serverInitTimeoutSeconds,
            serverStopTimeoutSeconds,
            memoryMb,
            javaExecutablePath,
            extraJvmArgs
        );

        final ServerProvider serverProvider = switch (Objects.requireNonNull(serverType).toLowerCase(Locale.ROOT))
        {
            case "spigot" -> new SpigotServerProvider(getLog(), serverSpecification);
            case "paper" -> new PaperServerProvider(getLog(), serverSpecification);
            default -> throw new MojoExecutionException(
                String.format(
                    "Unsupported server type: %s!\nSupported types: %s",
                    serverType,
                    SUPPORTED_SERVER_TYPES));
        };

        serverProvider.prepareServer();
    }
}
