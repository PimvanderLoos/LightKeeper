package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.provisioning.PluginArtifactSpec;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrepareServerInputResolverTest
{
    private static final PrepareServerInputResolver RESOLVER = new PrepareServerInputResolver();

    @Test
    void resolveWorldInputSpecs_shouldThrowExceptionWhenFolderSourceIsNotDirectory(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerWorldInputConfig config = new PrepareServerWorldInputConfig();
        setField(config, "name", "fixture");
        setField(config, "sourceType", "folder");
        setField(config, "sourcePath", Files.writeString(tempDirectory.resolve("not-a-directory.txt"), "x"));

        // execute + verify
        assertThatThrownBy(() -> RESOLVER.resolveWorldInputSpecs(List.of(config)))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("is not a directory");
    }

    @Test
    void resolveWorldInputSpecs_shouldThrowExceptionWhenArchiveSourceIsNotFile(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerWorldInputConfig config = new PrepareServerWorldInputConfig();
        setField(config, "name", "fixture");
        setField(config, "sourceType", "archive");
        setField(config, "sourcePath", Files.createDirectories(tempDirectory.resolve("not-a-file")));

        // execute + verify
        assertThatThrownBy(() -> RESOLVER.resolveWorldInputSpecs(List.of(config)))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("is not a regular file");
    }

    @Test
    void resolveWorldInputSpecs_shouldThrowExceptionWhenEnvironmentIsUnsupported(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerWorldInputConfig config = new PrepareServerWorldInputConfig();
        setField(config, "name", "fixture");
        setField(config, "sourceType", "folder");
        setField(config, "sourcePath", Files.createDirectories(tempDirectory.resolve("world")));
        setField(config, "environment", "moon");

        // execute + verify
        assertThatThrownBy(() -> RESOLVER.resolveWorldInputSpecs(List.of(config)))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Unsupported world environment");
    }

    @Test
    void resolvePluginArtifactSpecs_shouldThrowExceptionWhenMavenCoordinatesAreMissing()
        throws Exception
    {
        // setup
        final PrepareServerPluginArtifactConfig config = new PrepareServerPluginArtifactConfig();
        setField(config, "sourceType", "maven");
        setField(config, "artifactId", "fixture");
        setField(config, "version", "1.0.0");

        // execute + verify
        assertThatThrownBy(() -> RESOLVER.resolvePluginArtifactSpecs(List.of(config)))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("lightkeeper.plugins.groupId");
    }

    @Test
    void resolvePluginArtifactSpecs_shouldApplyDefaultsForMavenEntries()
        throws Exception
    {
        // setup
        final PrepareServerPluginArtifactConfig config = new PrepareServerPluginArtifactConfig();
        setField(config, "sourceType", "maven");
        setField(config, "groupId", "com.example");
        setField(config, "artifactId", "fixture");
        setField(config, "version", "1.0.0");

        // execute
        final List<PluginArtifactSpec> specs = RESOLVER.resolvePluginArtifactSpecs(List.of(config));

        // verify
        assertThat(specs).hasSize(1);
        assertThat(specs.getFirst().type()).isEqualTo("jar");
        assertThat(specs.getFirst().includeTransitive()).isFalse();
    }

    @Test
    void resolvePluginArtifactSpecs_shouldThrowExceptionWhenPathRenameDoesNotEndWithJar(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerPluginArtifactConfig config = new PrepareServerPluginArtifactConfig();
        setField(config, "sourceType", "path");
        setField(config, "path", Files.writeString(tempDirectory.resolve("fixture.jar"), "x"));
        setField(config, "renameTo", "fixture.zip");

        // execute + verify
        assertThatThrownBy(() -> RESOLVER.resolvePluginArtifactSpecs(List.of(config)))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("must end with .jar");
    }

    @Test
    void resolvePluginArtifactSpecs_shouldRequireSha256ForUrlSource()
        throws Exception
    {
        // setup
        final PrepareServerPluginArtifactConfig config = new PrepareServerPluginArtifactConfig();
        setField(config, "sourceType", "url");
        setField(config, "url", URI.create("https://example.com/plugin.jar"));

        // execute + verify
        assertThatThrownBy(() -> RESOLVER.resolvePluginArtifactSpecs(List.of(config)))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("lightkeeper.plugins.sha256");
    }

    @Test
    void resolvePluginArtifactSpecs_shouldResolveUrlSource()
        throws Exception
    {
        // setup
        final PrepareServerPluginArtifactConfig config = new PrepareServerPluginArtifactConfig();
        setField(config, "sourceType", "url");
        setField(config, "url", URI.create("https://example.com/downloads/plugin.jar"));
        setField(config, "sha256", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        // execute
        final List<PluginArtifactSpec> specs = RESOLVER.resolvePluginArtifactSpecs(List.of(config));

        // verify
        assertThat(specs).singleElement().satisfies(spec -> {
            assertThat(spec.sourceType()).isEqualTo(PluginArtifactSpec.SourceType.URL);
            assertThat(spec.renameTo()).isEqualTo("plugin.jar");
            assertThat(spec.uri()).isEqualTo(URI.create("https://example.com/downloads/plugin.jar"));
            assertThat(spec.sha256()).isEqualTo("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        });
    }

    @Test
    void resolvePluginArtifactSpecs_shouldResolveModrinthSource()
        throws Exception
    {
        // setup
        final PrepareServerPluginArtifactConfig config = new PrepareServerPluginArtifactConfig();
        setField(config, "sourceType", "modrinth");
        setField(config, "modrinthProject", "luckperms");
        setField(config, "modrinthVersion", "v5.5.0-bukkit");

        // execute
        final List<PluginArtifactSpec> specs = RESOLVER.resolvePluginArtifactSpecs(List.of(config));

        // verify
        assertThat(specs).singleElement().satisfies(spec -> {
            assertThat(spec.sourceType()).isEqualTo(PluginArtifactSpec.SourceType.MODRINTH);
            assertThat(spec.modrinthProject()).isEqualTo("luckperms");
            assertThat(spec.modrinthVersion()).isEqualTo("v5.5.0-bukkit");
            assertThat(spec.modrinthLoader()).isEqualTo("bukkit");
        });
    }

    @Test
    void resolvePluginArtifactSpecs_shouldRejectAmbiguousModrinthConfig()
        throws Exception
    {
        // setup
        final PrepareServerPluginArtifactConfig config = new PrepareServerPluginArtifactConfig();
        setField(config, "sourceType", "modrinth");
        setField(config, "modrinthVersionId", "ABCDEFGH");
        setField(config, "modrinthProject", "luckperms");

        // execute + verify
        assertThatThrownBy(() -> RESOLVER.resolvePluginArtifactSpecs(List.of(config)))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("may not combine");
    }

    private static void setField(Object target, String fieldName, Object value)
        throws Exception
    {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
