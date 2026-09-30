package nl.pim16aap2.lightkeeper.runtime;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Atomically publishes generated IDE runtime discovery metadata.
 */
public final class IdeRuntimeDiscoveryWriter
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Writes discovery metadata using same-filesystem atomic replacement.
     *
     * @param discovery Discovery metadata to publish.
     * @param path Destination path.
     * @throws IOException When serialization or atomic publication fails.
     */
    public void write(IdeRuntimeDiscovery discovery, Path path)
        throws IOException
    {
        final Path absolutePath = path.toAbsolutePath().normalize();
        final Path parent = absolutePath.getParent();
        if (parent == null)
            throw new IOException("IDE runtime discovery path '%s' has no parent directory.".formatted(path));
        Files.createDirectories(parent);

        final Path temporaryFile = Files.createTempFile(parent, "discovery-", ".json.tmp");
        try
        {
            try
            {
                objectMapper.writeValue(temporaryFile.toFile(), discovery);
            }
            catch (JacksonException exception)
            {
                throw new IOException("Failed to serialize IDE runtime discovery metadata.", exception);
            }
            Files.move(
                temporaryFile,
                absolutePath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
        catch (AtomicMoveNotSupportedException exception)
        {
            throw new IOException(
                "Filesystem does not support atomic IDE runtime discovery publication at '%s'."
                    .formatted(absolutePath),
                exception
            );
        }
        finally
        {
            Files.deleteIfExists(temporaryFile);
        }
    }
}
