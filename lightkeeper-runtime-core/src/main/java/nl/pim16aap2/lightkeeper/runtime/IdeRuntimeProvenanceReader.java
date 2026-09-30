package nl.pim16aap2.lightkeeper.runtime;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and validates IDE runtime provenance metadata.
 */
public final class IdeRuntimeProvenanceReader
{
    private final ObjectMapper objectMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    /**
     * Reads provenance metadata.
     *
     * @param path Provenance path.
     * @return Validated provenance.
     * @throws IOException When the document is missing, corrupt, or invalid.
     */
    public IdeRuntimeProvenance read(Path path)
        throws IOException
    {
        if (!Files.isRegularFile(path))
            throw new IOException("IDE runtime provenance file '%s' does not exist.".formatted(path));
        final IdeRuntimeProvenance provenance;
        try
        {
            provenance = objectMapper.readValue(path.toFile(), IdeRuntimeProvenance.class);
        }
        catch (JacksonException exception)
        {
            throw new IOException("Failed to parse IDE runtime provenance file '%s'.".formatted(path), exception);
        }
        validate(provenance);
        return provenance;
    }

    private static void validate(IdeRuntimeProvenance provenance)
        throws IOException
    {
        if (provenance.schemaVersion() != IdeRuntimeProvenance.SCHEMA_VERSION)
        {
            throw new IOException(
                "IDE runtime provenance schema mismatch. expected=%d actual=%d."
                    .formatted(IdeRuntimeProvenance.SCHEMA_VERSION, provenance.schemaVersion())
            );
        }
        requireNonBlank(provenance.moduleDirectory(), "moduleDirectory");
        requireNonBlank(provenance.executionId(), "executionId");
        requireNonBlank(provenance.serverType(), "serverType");
        requireNonBlank(provenance.preparationFingerprint(), "preparationFingerprint");
        if (provenance.runtimeProtocolVersion() <= 0)
            throw new IOException("IDE runtime provenance field 'runtimeProtocolVersion' is invalid.");
        if (provenance.requiredArtifacts().isEmpty())
            throw new IOException("IDE runtime provenance has no required artifacts.");
        provenance.requiredArtifacts().forEach(IdeRuntimeProvenanceReader::validateArtifact);
        provenance.sourceInputs().forEach(IdeRuntimeProvenanceReader::validateArtifact);
    }

    private static void validateArtifact(IdeRuntimeProvenance.Artifact artifact)
    {
        if (artifact.path() == null || artifact.path().isBlank() ||
            artifact.sha256() == null || !artifact.sha256().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("IDE runtime provenance contains an invalid artifact entry.");
    }

    private static void requireNonBlank(String value, String fieldName)
        throws IOException
    {
        if (value == null || value.isBlank())
            throw new IOException("IDE runtime provenance field '%s' is missing or blank.".formatted(fieldName));
    }
}
