package nl.pim16aap2.lightkeeper.provisioning.serverprovider;

import lombok.ToString;
import nl.pim16aap2.lightkeeper.provisioning.PaperBuildMetadata;
import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import nl.pim16aap2.lightkeeper.provisioning.ServerSpecification;
import nl.pim16aap2.lightkeeper.provisioning.serverprocess.MinecraftServerProcess;
import nl.pim16aap2.lightkeeper.provisioning.serverprocess.PaperServerProcess;
import nl.pim16aap2.lightkeeper.provisioning.util.HashUtil;

import java.nio.file.Path;

@ToString(callSuper = true)
public class PaperServerProvider extends ServerProvider
{
    private static final String SERVER_NAME = "paper";
    private final PaperBuildMetadata paperBuildMetadata;

    public PaperServerProvider(
        System.Logger log,
        ServerSpecification serverSpecification,
        PaperBuildMetadata paperBuildMetadata)
    {
        super(log, SERVER_NAME, serverSpecification);
        this.paperBuildMetadata = paperBuildMetadata;
    }

    @Override
    protected ServerProvider recreate(ServerSpecification specification)
    {
        return new PaperServerProvider(log(), specification, paperBuildMetadata);
    }

    @Override
    protected void createBaseServerJar()
        throws ProvisioningException
    {
        final Path targetFile = jarCacheFile();
        log().log(System.Logger.Level.INFO,
            "Downloading Paper build %d for version %s."
                .formatted(paperBuildMetadata.buildId(), paperBuildMetadata.minecraftVersion())
        );
        downloadFile(paperBuildMetadata.downloadUri().toString(), targetFile);

        final String actualHash = HashUtil.sha256(targetFile);
        if (!actualHash.equalsIgnoreCase(paperBuildMetadata.sha256()))
        {
            throw new ProvisioningException(
                "Checksum mismatch for downloaded Paper jar. Expected %s, got %s."
                    .formatted(paperBuildMetadata.sha256(), actualHash)
            );
        }
    }

    @Override
    protected void createBaseServer()
        throws ProvisioningException
    {
        acceptEula();
        writeServerProperties(createDefaultServerProperties());

        final int maxAttempts = Math.max(1, serverSpecification().serverStartMaxAttempts());

        for (int attempt = 1; attempt <= maxAttempts; ++attempt)
        {
            final MinecraftServerProcess serverProcess = createServerProcess();

            try
            {
                log().log(System.Logger.Level.INFO,
                    "LK_SERVER: Starting Paper base server process (attempt %d/%d)."
                        .formatted(attempt, maxAttempts)
                );
                serverProcess.start(serverSpecification().serverInitTimeoutSeconds());
                log().log(System.Logger.Level.INFO, "LK_SERVER: Paper base server started successfully.");
                log().log(System.Logger.Level.INFO, "LK_SERVER: Stopping Paper base server process.");
                serverProcess.stop(serverSpecification().serverStopTimeoutSeconds());
                log().log(System.Logger.Level.INFO, "LK_SERVER: Paper base server stopped successfully.");
                return;
            }
            catch (ProvisioningException exception)
            {
                if (attempt >= maxAttempts)
                {
                    throw new ProvisioningException(
                        "Paper server failed to start after %d attempt(s).".formatted(maxAttempts),
                        exception
                    );
                }

                log().log(System.Logger.Level.WARNING,
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
