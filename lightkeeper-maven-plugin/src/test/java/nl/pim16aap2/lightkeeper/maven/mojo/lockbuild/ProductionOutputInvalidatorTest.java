package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ProductionOutputInvalidatorTest
{
    @Mock
    private Log log;

    @Test
    void refresh_shouldClearGeneratedProductionOutputsAndKeepTestClasses(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenProject project = project(root);
        final Path obsolete = write(root, "target/classes/obsolete.txt", "stale");
        final Path generated = write(root, "target/generated-sources/stale.java", "stale");
        final Path testClass = write(root, "target/test-classes/ExistingIT.class", "test-bytecode");

        // execute
        ProductionOutputInvalidator.refresh(project, new Properties(), log);

        // verify
        assertThat(obsolete).doesNotExist();
        assertThat(generated).doesNotExist();
        assertThat(testClass).hasContent("test-bytecode");
        assertThat(root.resolve("src/main/java/Plugin.java")).exists();
    }

    @Test
    void refresh_shouldKeepIncrementalOutputsWhenInputsAreUnchanged(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenProject project = project(root);
        ProductionOutputInvalidator.refresh(project, new Properties(), log);
        final Path compiled = write(root, "target/classes/Plugin.class", "compiled");
        final Path generated = write(root, "target/generated-sources/Generated.java", "generated");

        // execute
        ProductionOutputInvalidator.refresh(project, new Properties(), log);

        // verify
        assertThat(compiled).hasContent("compiled");
        assertThat(generated).hasContent("generated");
    }

    @Test
    void refresh_shouldRemoveDeletedResourceOutputsBeforeMavenRebuilds(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenProject project = project(root);
        final Path resource = write(root, "src/main/resources/removed.txt", "resource");
        ProductionOutputInvalidator.refresh(project, new Properties(), log);
        final Path packagedResource = write(root, "target/classes/removed.txt", "resource");
        Files.delete(resource);

        // execute
        ProductionOutputInvalidator.refresh(project, new Properties(), log);

        // verify
        assertThat(packagedResource).doesNotExist();
    }

    @Test
    void refresh_shouldInvalidateOutputsAfterSourceChanges(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenProject project = project(root);
        ProductionOutputInvalidator.refresh(project, new Properties(), log);
        final Path compiled = write(root, "target/classes/Plugin$Gone.class", "old-bytecode");
        write(root, "src/main/java/Plugin.java", "public class Plugin { int updated; }");

        // execute
        ProductionOutputInvalidator.refresh(project, new Properties(), log);

        // verify
        assertThat(compiled).doesNotExist();
    }

    @Test
    void refresh_shouldInvalidateChangedEffectiveFilteringPropertiesWithoutPersistingTheirValues(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenProject project = project(root);
        final Properties properties = new Properties();
        properties.setProperty("private.build.setting", "private-initial-value");
        ProductionOutputInvalidator.refresh(project, properties, log);
        final Path compiled = write(root, "target/classes/Plugin.class", "compiled");
        properties.setProperty("private.build.setting", "private-updated-value");

        // execute
        ProductionOutputInvalidator.refresh(project, properties, log);

        // verify
        assertThat(compiled).doesNotExist();
        assertThat(root.resolve(".lightkeeper/build-inputs/production.json")).content()
            .doesNotContain("private.build.setting", "private-initial-value", "private-updated-value");
    }

    @Test
    void refresh_shouldKeepOutputsWhenOnlyTestControlFlagsChange(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenProject project = project(root);
        final Properties properties = new Properties();
        ProductionOutputInvalidator.refresh(project, properties, log);
        final Path compiled = write(root, "target/classes/Plugin.class", "compiled");
        properties.setProperty("skipITs", "true");
        properties.setProperty("skipTests", "true");
        properties.setProperty("it.test", "OtherIT");

        // execute
        ProductionOutputInvalidator.refresh(project, properties, log);

        // verify
        assertThat(compiled).hasContent("compiled");
    }

    @Test
    void refresh_shouldRejectProductionOutputOverlappingTheProject(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenProject project = project(root);
        project.getBuild().setOutputDirectory(root.toString());
        final Path source = root.resolve("src/main/java/Plugin.java");

        // execute
        final var failure = catchThrowable(() -> ProductionOutputInvalidator.refresh(project, new Properties(), log));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("overlaps");
        assertThat(source).exists();
    }

    @Test
    void refresh_shouldRejectProductionOutputContainingTestClasses(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenProject project = project(root);
        project.getBuild().setTestOutputDirectory(root.resolve("target/classes/tests").toString());
        final Path testClass = write(root, "target/classes/tests/ExistingIT.class", "test-bytecode");

        // execute
        final var failure = catchThrowable(() -> ProductionOutputInvalidator.refresh(project, new Properties(), log));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("overlaps");
        assertThat(testClass).hasContent("test-bytecode");
    }

    @Test
    void refresh_shouldSupportSeparateCustomOutputDirectories(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenProject project = project(root);
        project.getBuild().setOutputDirectory(root.resolve("custom/main").toString());
        project.getBuild().setTestOutputDirectory(root.resolve("custom/test").toString());
        final Path obsolete = write(root, "custom/main/obsolete.txt", "stale");
        final Path testClass = write(root, "custom/test/ExistingIT.class", "test-bytecode");

        // execute
        ProductionOutputInvalidator.refresh(project, new Properties(), log);

        // verify
        assertThat(obsolete).doesNotExist();
        assertThat(testClass).hasContent("test-bytecode");
    }

    private static MavenProject project(Path root)
        throws Exception
    {
        final Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId("example");
        model.setArtifactId("plugin");
        model.setVersion("1");
        model.setPackaging("jar");
        final Build build = new Build();
        build.setDirectory(root.resolve("target").toString());
        build.setOutputDirectory(root.resolve("target/classes").toString());
        build.setTestOutputDirectory(root.resolve("target/test-classes").toString());
        build.setSourceDirectory(root.resolve("src/main/java").toString());
        build.setTestSourceDirectory(root.resolve("src/test/java").toString());
        model.setBuild(build);
        final Resource resource = new Resource();
        resource.setDirectory(root.resolve("src/main/resources").toString());
        build.addResource(resource);
        final MavenProject project = new MavenProject(model);
        project.setFile(write(root, "pom.xml", "fixture").toFile());
        project.addCompileSourceRoot(build.getSourceDirectory());
        write(root, "src/main/java/Plugin.java", "public class Plugin {}");
        return project;
    }

    private static Path write(Path root, String relative, String content)
        throws Exception
    {
        final Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
