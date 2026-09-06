package nl.pim16aap2.lightkeeper.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates discovered IDE runtime ownership, compatibility, integrity, and examined local inputs.
 */
public final class IdeRuntimeValidator
{
    private IdeRuntimeValidator()
    {
    }

    /**
     * Validates a discovered runtime before Minecraft starts.
     *
     * @param discovery Discovery metadata.
     * @param moduleDirectory Owning module directory resolved from the test class.
     * @param manifest Runtime manifest.
     */
    public static void validate(
        IdeRuntimeDiscovery discovery,
        Path moduleDirectory,
        RuntimeManifest manifest)
    {
        final Path manifestPath = Path.of(discovery.runtimeManifestPath()).toAbsolutePath().normalize();
        final Path provenancePath = manifestPath.resolveSibling(IdeRuntimePaths.PROVENANCE_FILE_NAME);
        final IdeRuntimeProvenance provenance;
        try
        {
            provenance = new IdeRuntimeProvenanceReader().read(provenancePath);
        }
        catch (IOException | IllegalArgumentException exception)
        {
            throw invalid("provenance at '%s' is missing or invalid".formatted(provenancePath), exception);
        }

        final String canonicalModule = canonical(moduleDirectory);
        if (!provenance.moduleDirectory().equals(canonicalModule) ||
            !provenance.executionId().equals(discovery.executionId()) ||
            !provenance.serverType().equals(discovery.serverType()) ||
            !provenance.preparationFingerprint().equals(discovery.preparationFingerprint()) ||
            provenance.runtimeProtocolVersion() != RuntimeProtocol.VERSION ||
            manifest.runtimeProtocolVersion() != provenance.runtimeProtocolVersion())
        {
            throw invalid("discovery, provenance, and runtime protocol metadata do not match");
        }

        for (final IdeRuntimeProvenance.Artifact artifact : provenance.requiredArtifacts())
            validateRequiredArtifact(artifact);
        for (final IdeRuntimeProvenance.Artifact input : provenance.sourceInputs())
            validateExistingSourceInput(input);
    }

    private static void validateRequiredArtifact(IdeRuntimeProvenance.Artifact artifact)
    {
        final Path path = Path.of(artifact.path());
        if (!Files.exists(path))
            throw invalid("required prepared artifact '%s' is missing".formatted(path));
        validateHash(path, artifact.sha256(), "prepared artifact");
    }

    private static void validateExistingSourceInput(IdeRuntimeProvenance.Artifact input)
    {
        final Path path = Path.of(input.path());
        if (Files.notExists(path))
            return;
        validateHash(path, input.sha256(), "setup input");
    }

    private static void validateHash(Path path, String expectedHash, String kind)
    {
        try
        {
            final String actualHash = IdeRuntimeArtifactHasher.sha256(path);
            if (!actualHash.equals(expectedHash))
                throw invalid("%s '%s' changed since IDE setup".formatted(kind, path));
        }
        catch (IOException exception)
        {
            throw invalid("failed to validate %s '%s'".formatted(kind, path), exception);
        }
    }

    private static String canonical(Path path)
    {
        try
        {
            return path.toRealPath().toString();
        }
        catch (IOException exception)
        {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    private static IllegalStateException invalid(String cause)
    {
        return new IllegalStateException(invalidMessage(cause));
    }

    private static IllegalStateException invalid(String cause, Exception exception)
    {
        return new IllegalStateException(invalidMessage(cause), exception);
    }

    private static String invalidMessage(String cause)
    {
        return "IDE runtime is stale or invalid: " + cause + ". Rebuild changed artifacts and rerun "
            + "'mvn lightkeeper:prepare-server@<execution-id> -Dlightkeeper.ide=true'.";
    }
}
