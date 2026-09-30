package nl.pim16aap2.lightkeeper.runtime;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Atomically writes IDE runtime provenance metadata.
 */
public final class IdeRuntimeProvenanceWriter
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Writes provenance beside a prepared runtime.
     *
     * @param provenance Provenance to write.
     * @param path Destination path.
     * @throws IOException When serialization or atomic publication fails.
     */
    public void write(IdeRuntimeProvenance provenance, Path path)
        throws IOException
    {
        final Path destination = path.toAbsolutePath().normalize();
        final Path parent = destination.getParent();
        if (parent == null)
            throw new IOException("IDE runtime provenance path '%s' has no parent directory.".formatted(path));
        Files.createDirectories(parent);
        final Path temporaryFile = Files.createTempFile(parent, "provenance-", ".json.tmp");
        try
        {
            try
            {
                objectMapper.writeValue(temporaryFile.toFile(), provenance);
            }
            catch (JacksonException exception)
            {
                throw new IOException("Failed to serialize IDE runtime provenance metadata.", exception);
            }
            Files.move(
                temporaryFile,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
        finally
        {
            Files.deleteIfExists(temporaryFile);
        }
    }
}
