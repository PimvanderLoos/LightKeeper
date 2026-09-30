package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdeRuntimeProvenanceReaderTest
{
    private static final String SHA_256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void read_shouldAcceptValidProvenance(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path provenancePath = tempDirectory.resolve("provenance.json");
        final IdeRuntimeProvenance provenance = provenance(tempDirectory);
        new IdeRuntimeProvenanceWriter().write(provenance, provenancePath);

        // execute
        final IdeRuntimeProvenance result = new IdeRuntimeProvenanceReader().read(provenancePath);

        // verify
        assertThat(result).isEqualTo(provenance);
    }

    @Test
    void read_shouldRejectMissingProvenance(@TempDir Path tempDirectory)
    {
        // setup
        final Path provenancePath = tempDirectory.resolve("missing.json");

        // execute + verify
        assertThatThrownBy(() -> new IdeRuntimeProvenanceReader().read(provenancePath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("does not exist");
    }

    @Test
    void read_shouldRejectCorruptProvenance(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path provenancePath = Files.writeString(tempDirectory.resolve("provenance.json"), "not-json");

        // execute + verify
        assertThatThrownBy(() -> new IdeRuntimeProvenanceReader().read(provenancePath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Failed to parse");
    }

    @Test
    void read_shouldRejectIncompatibleSchema(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path provenancePath = tempDirectory.resolve("provenance.json");
        final IdeRuntimeProvenance provenance = provenance(tempDirectory);
        new IdeRuntimeProvenanceWriter().write(new IdeRuntimeProvenance(
            IdeRuntimeProvenance.SCHEMA_VERSION + 1,
            provenance.moduleDirectory(),
            provenance.executionId(),
            provenance.serverType(),
            provenance.preparationFingerprint(),
            provenance.runtimeProtocolVersion(),
            provenance.requiredArtifacts(),
            provenance.sourceInputs()
        ), provenancePath);

        // execute + verify
        assertThatThrownBy(() -> new IdeRuntimeProvenanceReader().read(provenancePath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("schema mismatch");
    }

    @ParameterizedTest
    @ValueSource(strings = {"moduleDirectory", "executionId", "serverType", "preparationFingerprint"})
    void read_shouldRejectBlankRequiredField(String blankField, @TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path provenancePath = tempDirectory.resolve("provenance.json");
        final IdeRuntimeProvenance provenance = provenance(tempDirectory);
        new IdeRuntimeProvenanceWriter().write(new IdeRuntimeProvenance(
            provenance.schemaVersion(),
            blankField.equals("moduleDirectory") ? " " : provenance.moduleDirectory(),
            blankField.equals("executionId") ? " " : provenance.executionId(),
            blankField.equals("serverType") ? " " : provenance.serverType(),
            blankField.equals("preparationFingerprint") ? " " : provenance.preparationFingerprint(),
            provenance.runtimeProtocolVersion(),
            provenance.requiredArtifacts(),
            provenance.sourceInputs()
        ), provenancePath);

        // execute + verify
        assertThatThrownBy(() -> new IdeRuntimeProvenanceReader().read(provenancePath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining(blankField);
    }

    @Test
    void read_shouldRejectInvalidProtocolAndArtifacts(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path invalidProtocolPath = tempDirectory.resolve("invalid-protocol.json");
        final Path missingArtifactsPath = tempDirectory.resolve("missing-artifacts.json");
        final Path invalidArtifactPath = tempDirectory.resolve("invalid-artifact.json");
        final IdeRuntimeProvenance provenance = provenance(tempDirectory);
        new IdeRuntimeProvenanceWriter().write(copyWith(provenance, 0, provenance.requiredArtifacts()),
            invalidProtocolPath);
        new IdeRuntimeProvenanceWriter().write(copyWith(provenance, RuntimeProtocol.VERSION, List.of()),
            missingArtifactsPath);
        new IdeRuntimeProvenanceWriter().write(copyWith(
            provenance,
            RuntimeProtocol.VERSION,
            List.of(new IdeRuntimeProvenance.Artifact("artifact.jar", "invalid"))
        ), invalidArtifactPath);

        // execute + verify
        assertThatThrownBy(() -> new IdeRuntimeProvenanceReader().read(invalidProtocolPath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("runtimeProtocolVersion");
        assertThatThrownBy(() -> new IdeRuntimeProvenanceReader().read(missingArtifactsPath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("no required artifacts");
        assertThatThrownBy(() -> new IdeRuntimeProvenanceReader().read(invalidArtifactPath))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid artifact");
    }

    private static IdeRuntimeProvenance provenance(Path moduleDirectory)
    {
        return new IdeRuntimeProvenance(
            IdeRuntimeProvenance.SCHEMA_VERSION,
            moduleDirectory.toAbsolutePath().normalize().toString(),
            "prepare-paper",
            "paper",
            "fingerprint",
            RuntimeProtocol.VERSION,
            List.of(new IdeRuntimeProvenance.Artifact(moduleDirectory.resolve("runtime-manifest.json").toString(),
                SHA_256)),
            List.of(new IdeRuntimeProvenance.Artifact(moduleDirectory.resolve("plugin.jar").toString(), SHA_256))
        );
    }

    private static IdeRuntimeProvenance copyWith(
        IdeRuntimeProvenance provenance,
        int protocolVersion,
        List<IdeRuntimeProvenance.Artifact> requiredArtifacts)
    {
        return new IdeRuntimeProvenance(
            provenance.schemaVersion(),
            provenance.moduleDirectory(),
            provenance.executionId(),
            provenance.serverType(),
            provenance.preparationFingerprint(),
            protocolVersion,
            requiredArtifacts,
            provenance.sourceInputs()
        );
    }
}
