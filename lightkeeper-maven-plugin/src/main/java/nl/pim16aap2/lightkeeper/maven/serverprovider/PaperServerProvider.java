package nl.pim16aap2.lightkeeper.maven.serverprovider;

import lombok.ToString;
import nl.pim16aap2.lightkeeper.maven.PaperBuildMetadata;
import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import nl.pim16aap2.lightkeeper.maven.serverprocess.MinecraftServerProcess;
import nl.pim16aap2.lightkeeper.maven.serverprocess.PaperServerProcess;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.nio.file.Path;

@ToString(callSuper = true)
public class PaperServerProvider extends ServerProvider
{
    private static final String SERVER_NAME = "paper";
    private final PaperBuildMetadata paperBuildMetadata;

    public PaperServerProvider(
        Log log,
        ServerSpecification serverSpecification,
        PaperBuildMetadata paperBuildMetadata)
    {
        super(log, SERVER_NAME, serverSpecification);
        this.paperBuildMetadata = paperBuildMetadata;
    }

    @Override
    protected void createBaseServerJar()
        throws MojoExecutionException
    {
        final Path targetFile = jarCacheFile();
        log().info(
            "Downloading Paper build %d for version %s."
                .formatted(paperBuildMetadata.buildId(), paperBuildMetadata.minecraftVersion())
        );
        downloadFile(paperBuildMetadata.downloadUri().toString(), targetFile);

        final String actualHash = HashUtil.sha256(targetFile);
        if (!actualHash.equalsIgnoreCase(paperBuildMetadata.sha256()))
        {
            throw new MojoExecutionException(
                "Checksum mismatch for downloaded Paper jar. Expected %s, got %s."
                    .formatted(paperBuildMetadata.sha256(), actualHash)
            );
        }
    }

    @Override
    protected void createBaseServer()
        throws MojoExecutionException
    {
        acceptEula();
        writeServerProperties(createDefaultServerProperties());

        final int maxAttempts = Math.max(1, serverSpecification().serverStartMaxAttempts());

        for (int attempt = 1; attempt <= maxAttempts; ++attempt)
        {
            final MinecraftServerProcess serverProcess = createServerProcess();

            try
            {
                log().info(
                    "LK_SERVER: Starting Paper base server process (attempt %d/%d)."
                        .formatted(attempt, maxAttempts)
                );
                serverProcess.start(serverSpecification().serverInitTimeoutSeconds());
                log().info("LK_SERVER: Paper base server started successfully.");
                log().info("LK_SERVER: Stopping Paper base server process.");
                serverProcess.stop(serverSpecification().serverStopTimeoutSeconds());
                log().info("LK_SERVER: Paper base server stopped successfully.");
                return;
            }
            catch (MojoExecutionException exception)
            {
                if (attempt >= maxAttempts)
                {
                    throw new MojoExecutionException(
                        "Paper server failed to start after %d attempt(s).".formatted(maxAttempts),
                        exception
                    );
                }

                log().warn(
                    ("LK_RETRY_STARTUP_PROCESS_ONLY: Paper startup attempt %d/%d failed. " +
                        "Retrying process startup without re-downloading or rebuilding cache. Cause: %s")
                        .formatted(attempt, maxAttempts, exception.getMessage())
                );
                cleanTransientFilesForRetry();
            }
        }
    }

    protected MinecraftServerProcess createServerProcess()
    {
        return new PaperServerProcess(
            baseServerDirectory(),
            baseServerJarFile(),
            serverSpecification().javaExecutablePath(),
            serverSpecification().extraJvmArgs(),
            serverSpecification().memoryMb()
        );
    }
}
