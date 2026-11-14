package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.MavenBuildContext;
import nl.pim16aap2.lightkeeper.runtime.ModulePlanPaths;
import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;
import nl.pim16aap2.lightkeeper.runtime.TestModuleAnchor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class LightkeeperRuntimeResolverTest
{
    @AfterEach
    void clearRuntimeManifestProperty()
    {
        System.clearProperty(LightkeeperRuntimeResolver.RUNTIME_MANIFEST_PROPERTY);
    }

    @Test
    void resolveFromClassOutput_shouldPreferExplicitManifest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path explicitManifest = Files.writeString(tempDirectory.resolve("explicit.json"), "{}");
        System.setProperty(
            LightkeeperRuntimeResolver.RUNTIME_MANIFEST_PROPERTY,
            explicitManifest.toString()
        );

        // execute
        final LightkeeperRuntimeResolver.ResolvedRuntime resolved = LightkeeperRuntimeResolver.resolveFromClassOutput(
            tempDirectory.resolve("unsupported-output-layout")
        );

        // verify
        assertThat(resolved.manifestPath()).isEqualTo(explicitManifest.toAbsolutePath().normalize());
        assertThat(resolved.modulePlan()).isNull();
    }

    @Test
    void resolveFromClassOutput_shouldNotFallBackWhenExplicitManifestIsInvalid(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path moduleDirectory = Files.createDirectories(tempDirectory.resolve("module"));
        final Path classOutput = Files.createDirectories(moduleDirectory.resolve("target/test-classes"));
        writeModulePlan(moduleDirectory, classOutput);
        final Path missingExplicitManifest = tempDirectory.resolve("missing.json");
        System.setProperty(
            LightkeeperRuntimeResolver.RUNTIME_MANIFEST_PROPERTY,
            missingExplicitManifest.toString()
        );

        // execute + verify
        assertThatThrownBy(() -> LightkeeperRuntimeResolver.resolveFromClassOutput(classOutput))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Explicit runtime manifest")
            .hasMessageContaining(missingExplicitManifest.toString());
    }

    @Test
    void resolveFromClassOutput_shouldUseMavensCustomOutputWithoutExplicitProperty(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path moduleDirectory = Files.createDirectories(tempDirectory.resolve("module"));
        final Path classOutput = Files.createDirectories(moduleDirectory.resolve("custom/nested/test-output"));
        final ModuleTestPlan plan = writeModulePlan(moduleDirectory, classOutput);

        // execute
        final LightkeeperRuntimeResolver.ResolvedRuntime resolved =
            LightkeeperRuntimeResolver.resolveFromClassOutput(classOutput);

        // verify
        assertThat(resolved.manifestPath()).isNull();
        assertThat(resolved.modulePlan()).isEqualTo(plan);
    }

    @Test
    void resolveFromClassOutput_shouldRejectAnAnchorCopiedFromAnotherOutput(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path module = Files.createDirectories(tempDirectory.resolve("module"));
        final Path owned = Files.createDirectories(module.resolve("custom/owned"));
        final Path unrelated = Files.createDirectories(module.resolve("custom/unrelated"));
        writeModulePlan(module, owned);
        Files.createDirectories(ModulePlanPaths.classOutputAnchor(unrelated).getParent());
        Files.copy(ModulePlanPaths.classOutputAnchor(owned), ModulePlanPaths.classOutputAnchor(unrelated));

        // execute
        final Throwable failure = catchThrowable(() -> LightkeeperRuntimeResolver.resolveFromClassOutput(unrelated));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("Test-class ownership anchor belongs to another output directory.");
    }

    @Test
    void resolve_shouldPreferExplicitManifestForTestClass(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path explicitManifest = Files.writeString(tempDirectory.resolve("explicit.json"), "{}");
        System.setProperty(LightkeeperRuntimeResolver.RUNTIME_MANIFEST_PROPERTY, explicitManifest.toString());

        // execute
        final LightkeeperRuntimeResolver.ResolvedRuntime resolved =
            LightkeeperRuntimeResolver.resolveRuntime(LightkeeperRuntimeResolverTest.class);

        // verify
        assertThat(resolved.manifestPath()).isEqualTo(explicitManifest.toAbsolutePath().normalize());
        assertThat(resolved.modulePlan()).isNull();
    }

    private static ModuleTestPlan writeModulePlan(Path moduleDirectory, Path classOutput)
        throws Exception
    {
        final Path pom = Files.writeString(moduleDirectory.resolve("pom.xml"), "<project/>");
        final ModuleTestPlan plan = new ModuleTestPlan(
            ModuleTestPlan.SCHEMA_VERSION, moduleDirectory.toRealPath().toString(), pom.toString(), pom.toString(),
            classOutput.toString(), List.of("default"), "default",
            new MavenBuildContext("mvn", System.getProperty("java.home"), List.of(), List.of(), Map.of(),
                null, null, null, null)
        );
        GeneratedMetadata.write(plan, ModulePlanPaths.modulePlan(moduleDirectory));
        GeneratedMetadata.write(new TestModuleAnchor(TestModuleAnchor.SCHEMA_VERSION, plan.moduleDirectory(),
            plan.classOutputDirectory()), ModulePlanPaths.classOutputAnchor(classOutput));
        return plan;
    }
}
