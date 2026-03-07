package nl.pim16aap2.lightkeeper.maven.serverprovider;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites target-server {@code server.properties} with runtime-specific networking values.
 */
final class TargetServerPropertiesConfigurer
{
    private TargetServerPropertiesConfigurer()
    {
    }

    /**
     * Rewrites {@code server-port} and {@code query.port} in the target server properties file.
     *
     * @param log
     *     Logger for lifecycle messages.
     * @param targetServerPropertiesFile
     *     Path to target {@code server.properties}.
     * @param port
     *     Runtime port to apply.
     * @throws MojoExecutionException
     *     If reading or writing properties fails.
     */
    static void rewriteWithRuntimePort(Log log, Path targetServerPropertiesFile, int port)
        throws MojoExecutionException
    {
        final List<String> lines;
        try
        {
            lines = Files.readAllLines(targetServerPropertiesFile, StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to read target server properties '%s'."
                    .formatted(targetServerPropertiesFile),
                exception
            );
        }

        final List<String> updatedLines = new ArrayList<>(lines.size() + 2);
        boolean serverPortUpdated = false;
        boolean queryPortUpdated = false;
        for (String line : lines)
        {
            if (line.startsWith("server-port="))
            {
                updatedLines.add("server-port=" + port);
                serverPortUpdated = true;
                continue;
            }
            if (line.startsWith("query.port="))
            {
                updatedLines.add("query.port=" + port);
                queryPortUpdated = true;
                continue;
            }
            updatedLines.add(line);
        }

        if (!serverPortUpdated)
            updatedLines.add("server-port=" + port);
        if (!queryPortUpdated)
            updatedLines.add("query.port=" + port);

        try
        {
            Files.write(targetServerPropertiesFile, updatedLines, StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to write updated target server properties '%s'."
                    .formatted(targetServerPropertiesFile),
                exception
            );
        }

        log.info("Assigned target server port %d in '%s'.".formatted(port, targetServerPropertiesFile));
    }
}
