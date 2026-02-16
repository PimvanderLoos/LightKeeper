package nl.pim16aap2.lightkeeper.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrepareServerMojoTest
{
    @Test
    void ensureRuntimeManifestParentDirectoryExists_shouldCreateMissingParentDirectory(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path runtimeManifestPath = tempDirectory.resolve("runtime-manifests").resolve("runtime-manifest.json");
        assertThat(runtimeManifestPath.getParent()).doesNotExist();

        // execute
        PrepareServerMojo.ensureRuntimeManifestParentDirectoryExists(runtimeManifestPath);

        // verify
        assertThat(runtimeManifestPath.getParent()).isDirectory();
    }

    @Test
    void ensureRuntimeManifestParentDirectoryExists_shouldIgnorePathsWithoutParent()
        throws Exception
    {
        // setup
        final Path runtimeManifestPath = Path.of("runtime-manifest.json");
        assertThat(runtimeManifestPath.getParent()).isNull();

        // execute
        PrepareServerMojo.ensureRuntimeManifestParentDirectoryExists(runtimeManifestPath);

        // verify
        assertThat(runtimeManifestPath.getParent()).isNull();
    }

    @Test
    void validateExtraJvmArgs_shouldAcceptNullAndPlainValues()
    {
        // setup
        final String plainValue = "-javaagent:/tmp/jacoco.jar=destfile=/tmp/jacoco.exec,append=true";

        // execute
        final var nullValidation = assertThatCode(() -> PrepareServerMojo.validateExtraJvmArgs(null));
        final var blankValidation = assertThatCode(() -> PrepareServerMojo.validateExtraJvmArgs("   "));
        final var plainValidation = assertThatCode(() -> PrepareServerMojo.validateExtraJvmArgs(plainValue));

        // verify
        nullValidation.doesNotThrowAnyException();
        blankValidation.doesNotThrowAnyException();
        plainValidation.doesNotThrowAnyException();
    }

    @Test
    void validateExtraJvmArgs_shouldThrowExceptionWhenPlaceholderIsUnresolved()
    {
        // setup
        final String unresolved = "${lightkeeper.server.jacocoArgLine}";

        // execute
        final var thrownBy = assertThatThrownBy(() -> PrepareServerMojo.validateExtraJvmArgs(unresolved));

        // verify
        thrownBy
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("lightkeeper.extraJvmArgs")
            .hasMessageContaining(unresolved);
    }
}
