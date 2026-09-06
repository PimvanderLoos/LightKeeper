package nl.pim16aap2.lightkeeper.runtime;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads generated IDE runtime discovery metadata.
 */
public final class IdeRuntimeDiscoveryReader
{
    private final ObjectMapper objectMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    /**
     * Reads and validates discovery metadata.
     *
     * @param path Discovery metadata path.
     * @return The validated discovery metadata.
     * @throws IOException When the document is missing, corrupt, or incompatible.
     */
    public IdeRuntimeDiscovery read(Path path)
        throws IOException
    {
        if (!Files.isRegularFile(path))
            throw new IOException("IDE runtime discovery file '%s' does not exist.".formatted(path));

        final IdeRuntimeDiscovery discovery;
        try
        {
            discovery = objectMapper.readValue(path.toFile(), IdeRuntimeDiscovery.class);
        }
        catch (JacksonException exception)
        {
            throw new IOException("Failed to parse IDE runtime discovery file '%s'.".formatted(path), exception);
        }
        validate(discovery);
        return discovery;
    }

    private static void validate(IdeRuntimeDiscovery discovery)
        throws IOException
    {
        if (discovery.schemaVersion() != IdeRuntimeDiscovery.SCHEMA_VERSION)
        {
            throw new IOException(
                "IDE runtime discovery schema mismatch. expected=%d actual=%d."
                    .formatted(IdeRuntimeDiscovery.SCHEMA_VERSION, discovery.schemaVersion())
            );
        }
        requireNonBlank(discovery.moduleDirectory(), "moduleDirectory");
        requireNonBlank(discovery.executionId(), "executionId");
        requireNonBlank(discovery.serverType(), "serverType");
        requireNonBlank(discovery.runtimeManifestPath(), "runtimeManifestPath");
        requireNonBlank(discovery.preparationFingerprint(), "preparationFingerprint");
    }

    private static void requireNonBlank(String value, String fieldName)
        throws IOException
    {
        if (value == null || value.isBlank())
            throw new IOException("IDE runtime discovery field '%s' is missing or blank.".formatted(fieldName));
    }
}
