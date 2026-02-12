package nl.pim16aap2.lightkeeper.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes runtime manifests.
 */
public final class RuntimeManifestWriter
{
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(RuntimeManifest runtimeManifest, Path path)
        throws MojoExecutionException
    {
        try
        {
            final Path parent = path.getParent();
            if (parent != null)
                Files.createDirectories(parent);
            objectMapper.writeValue(path.toFile(), runtimeManifest);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException("Failed to write runtime manifest to '%s'.".formatted(path), exception);
        }
    }
}
