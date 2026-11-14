package nl.pim16aap2.lightkeeper.provisioning.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.*;

class PluginDescriptorValidatorTest
{
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"plugin.yml", "paper-plugin.yml"})
    void validate_shouldAcceptMetadataWithoutRequiringMainClassOrCompatibleApi(String descriptor)
        throws Exception
    {
        // setup
        final var artifact = jar(descriptor, "name: Example\nversion: 1.0\nmain: absent.Main\napi-version: '99.0'\n");

        // execute
        final var result = assertThatCode(() -> PluginDescriptorValidator.validate(artifact, "example:plugin"));

        // verify
        result.doesNotThrowAnyException();
    }

    @Test
    void validate_shouldAllowIntentionallyMissingPluginDependency()
        throws Exception
    {
        // setup
        final var artifact = jar("plugin.yml", "name: Example\nversion: 1\nmain: absent.Main\ndepend: [Vault]\n");

        // execute
        final var result = assertThatCode(() -> PluginDescriptorValidator.validate(artifact, "example:plugin"));

        // verify
        result.doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "name: Example\nversion: 1\n",
        "name: Example\nmain: example.Main\n",
        "version: 1\nmain: example.Main\n",
        "name: [Not, Scalar]\nversion: 1\nmain: example.Main\n",
        "name: Example\nname: Duplicate\nversion: 1\nmain: example.Main\n",
        "name: Example\nversion: 1\nmain: example.Main\nload: INVALID\n",
        "name: Example\nversion: 1\nmain: example.Main\ndepend: Vault\n",
        "!!java.net.URL [https://example.com]",
        "name: ["
    })
    void validate_shouldRejectMalformedOrInvalidDescriptors(String contents)
        throws Exception
    {
        // setup
        final var artifact = jar("plugin.yml", contents);

        // execute
        final var result = assertThatThrownBy(() -> PluginDescriptorValidator.validate(artifact, "example:plugin"));

        // verify
        result.hasMessageContaining("example:plugin").hasMessageContaining(artifact.sourceJar().toString());
    }

    @Test
    void validate_shouldRejectMissingPaperApiVersion()
        throws Exception
    {
        // setup
        final var artifact = jar("paper-plugin.yml", "name: Example\nversion: 1\nmain: example.Main\n");

        // execute
        final var result = assertThatThrownBy(() -> PluginDescriptorValidator.validate(artifact, "example:plugin"));

        // verify
        result.hasMessageContaining("api-version");
    }

    @Test
    void validate_shouldRejectArchiveWithoutPluginMetadata()
        throws Exception
    {
        // setup
        final var artifact = jar("unrelated.yml", "name: Example\n");

        // execute
        final var result = assertThatThrownBy(() -> PluginDescriptorValidator.validate(artifact, "example:plugin"));

        // verify
        result.hasMessageContaining("neither plugin.yml nor paper-plugin.yml");
    }

    @Test
    void validate_shouldRejectCorruptJar()
        throws Exception
    {
        // setup
        final Path broken = Files.writeString(directory.resolve("broken.jar"), "not an archive");
        final var artifact = new ResolvedPluginArtifact(broken, "broken.jar", "path:" + broken);

        // execute
        final var result = assertThatThrownBy(() -> PluginDescriptorValidator.validate(artifact, "example:plugin"));

        // verify
        result.hasMessageContaining("broken.jar").hasMessageContaining("Invalid plugin under test");
    }

    @Test
    void validate_shouldRejectOversizedDescriptor()
        throws Exception
    {
        // setup
        final var artifact = jar("plugin.yml", "description: " + "x".repeat(256 * 1024));

        // execute
        final var result = assertThatThrownBy(() -> PluginDescriptorValidator.validate(artifact, "example:plugin"));

        // verify
        result.hasMessageContaining("at most");
    }

    private ResolvedPluginArtifact jar(String name, String contents)
        throws Exception
    {
        final Path path = directory.resolve("plugin.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(path)))
        {
            output.putNextEntry(new JarEntry(name));
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return new ResolvedPluginArtifact(path, "plugin.jar", "path:" + path);
    }
}
