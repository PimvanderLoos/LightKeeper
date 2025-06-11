package nl.pim16aap2.lightkeeper.maven;

import lombok.AllArgsConstructor;
import nl.pim16aap2.lightkeeper.maven.serverprovider.PaperServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.ServerProvider;
import nl.pim16aap2.lightkeeper.maven.serverprovider.SpigotServerProvider;
import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Mojo(name = "prepare-server", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
@AllArgsConstructor
public class PrepareServerMojo extends AbstractMojo
{
    private static final List<String> SUPPORTED_SERVER_TYPES = List.of("spigot", "paper");

    @Parameter(property = "lightkeeper.serverType", defaultValue = "spigot")
    private String serverType;

    @Parameter(property = "lightkeeper.serverVersion", defaultValue = "1.21.5")
    private String serverVersion;

    @Parameter(
        property = "lightkeeper.jarCacheDirectory",
        defaultValue = "${settings.localRepository}/nl/pim16aap2/lightkeeper/cache/jars"
    )
    private Path jarCacheDirectory;

    @Parameter(
        property = "lightkeeper.baseServerCacheDirectory",
        defaultValue = "${settings.localRepository}/nl/pim16aap2/lightkeeper/cache/server"
    )
    private Path baseServerCacheDirectory;

    @Parameter(
        property = "lightkeeper.serverWorkDirectory",
        defaultValue = "${project.build.directory}/lightkeeper-server", required = true
    )
    private Path serverWorkDirectory;

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

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        FileUtil.createDirectories(jarCacheDirectory, "jar cache directory");
        FileUtil.createDirectories(baseServerCacheDirectory, "base server cache directory");
        FileUtil.createDirectories(serverWorkDirectory, "server work directory");

        final ServerProvider serverProvider = switch (Objects.requireNonNull(serverType).toLowerCase(Locale.ROOT))
        {
            case "spigot" -> new SpigotServerProvider(getLog());
            case "paper" -> new PaperServerProvider(getLog());
            default -> throw new MojoExecutionException(
                String.format(
                    "Unsupported server type: %s!\nSupported types: %s",
                    serverType,
                    SUPPORTED_SERVER_TYPES));
        };

        getLog().info("Using server provider: " + serverProvider.getName());
    }
}
