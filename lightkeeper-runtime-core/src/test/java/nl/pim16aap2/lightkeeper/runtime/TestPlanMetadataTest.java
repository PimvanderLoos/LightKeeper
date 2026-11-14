package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

class TestPlanMetadataTest
{
    @Test
    void retainProperties_shouldExcludeRepositoryCredentialsAndInvocationControls()
    {
        // setup
        final Properties properties = new Properties();
        properties.setProperty("revision", "2.0");
        properties.setProperty("lightkeeper.serverType", "spigot");
        properties.setProperty("nexus.password", "private-password");
        properties.setProperty("repository.token", "private-token");
        properties.setProperty("skipITs", "true");
        properties.setProperty("it.test", "OldSelectionIT");

        // execute
        final Map<String, String> retained = MavenBuildContext.retainProperties(properties);

        // verify
        assertThat(retained).containsExactlyInAnyOrderEntriesOf(Map.of(
            "revision", "2.0",
            "lightkeeper.serverType", "spigot"
        ));
    }

    @Test
    void buildContext_shouldRejectArbitraryAuthenticationProperties(@TempDir Path root)
    {
        // setup
        final Map<String, String> unsafe = Map.of("nexus.password", "private-password");

        // execute
        final var failure = catchThrowable(() -> context(root, unsafe));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unsupported retained");
    }

    @Test
    void buildContext_shouldFreezeCallerOwnedProfilesAndProperties(@TempDir Path root)
    {
        // setup
        final var profiles = new ArrayList<>(List.of("e2e"));
        final var properties = new HashMap<>(Map.of("revision", "1"));
        final MavenBuildContext context = new MavenBuildContext(
            "mvn", root.toString(), profiles, List.of("legacy"), properties,
            root.resolve("settings.xml").toString(), null, root.resolve("toolchains.xml").toString(), null
        );

        // execute
        profiles.add("other");
        properties.put("revision", "changed");

        // verify
        assertThat(context.activeProfiles()).containsExactly("e2e");
        assertThat(context.inactiveProfiles()).containsExactly("legacy");
        assertThat(context.userProperties()).containsEntry("revision", "1");
    }

    @Test
    void modulePlan_shouldRoundTripCustomOutputsAndReactorScope(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path module = root.resolve("consumer-tests");
        final Path output = root.resolve("custom-layout/integration-classes");
        final ModuleTestPlan plan = new ModuleTestPlan(
            ModuleTestPlan.SCHEMA_VERSION, module.toString(), module.resolve("test-pom.xml").toString(),
            root.resolve("reactor-pom.xml").toString(), output.toString(), List.of("paper", "spigot"), "paper",
            context(root, Map.of("revision", "2"))
        );

        // execute
        GeneratedMetadata.write(plan, ModulePlanPaths.modulePlan(module));
        GeneratedMetadata.write(
            new TestModuleAnchor(TestModuleAnchor.SCHEMA_VERSION, module.toString(), output.toString()),
            ModulePlanPaths.classOutputAnchor(output)
        );
        final ModuleTestPlan parsed = GeneratedMetadata.read(ModulePlanPaths.modulePlan(module), ModuleTestPlan.class);
        final TestModuleAnchor anchor = GeneratedMetadata.read(
            ModulePlanPaths.classOutputAnchor(output),
            TestModuleAnchor.class
        );

        // verify
        assertThat(parsed).isEqualTo(plan);
        assertThat(parsed.reactorDirectory()).isEqualTo(root);
        assertThat(anchor.classOutputDirectory()).isEqualTo(output.toString());
        assertThat(anchor.moduleDirectory()).isEqualTo(module.toString());
    }

    @Test
    void modulePlan_shouldRejectAProjectFileOwnedByAnotherModule(@TempDir Path root)
    {
        // setup
        final Path module = root.resolve("module");

        // execute
        final var failure = catchThrowable(() -> new ModuleTestPlan(
            ModuleTestPlan.SCHEMA_VERSION, module.toString(), root.resolve("other/pom.xml").toString(),
            root.resolve("pom.xml").toString(), module.resolve("target/test-classes").toString(),
            List.of("paper"), "paper", context(root, Map.of())
        ));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("owning module");
    }

    @Test
    void modulePlan_shouldRejectDuplicateVariants(@TempDir Path root)
    {
        // setup
        final MavenBuildContext context = context(root, Map.of());

        // execute
        final var failure = catchThrowable(() -> new ModuleTestPlan(
            ModuleTestPlan.SCHEMA_VERSION, root.toString(), root.resolve("pom.xml").toString(),
            root.resolve("pom.xml").toString(), root.resolve("classes").toString(),
            List.of("paper", "paper"), "paper", context
        ));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distinct");
    }

    @Test
    void runtimeContext_shouldRetainVariantSnapshotAndRealProcessIdentityWithoutAMavenFlag(@TempDir Path root)
        throws Exception
    {
        // setup
        final EffectiveTestPlan plan = effectivePlan(root, "paper-it");
        final MavenRuntimeContext context = new MavenRuntimeContext(
            MavenRuntimeContext.SCHEMA_VERSION, plan,
            ModulePlanPaths.snapshotsDirectory(root).resolve("snapshot/snapshot.json").toString(),
            ProcessIdentity.current(), "1".repeat(32)
        );
        final Path file = ModulePlanPaths.runtimeContext(root, "paper-it");

        // execute
        GeneratedMetadata.write(context, file);
        final MavenRuntimeContext parsed = GeneratedMetadata.read(file, MavenRuntimeContext.class);
        final String serialized = Files.readString(file);

        // verify
        assertThat(parsed).isEqualTo(context);
        assertThat(parsed.producer().matchesCurrentAncestry()).isTrue();
        assertThat(serialized).doesNotContain("matchesCurrentAncestry", "currentProcessAncestor", "mavenMode");
    }

    @Test
    void runtimeContext_shouldRejectCrossOwnedSnapshots(@TempDir Path root)
    {
        // setup
        final EffectiveTestPlan plan = effectivePlan(root.resolve("owning"), "paper-it");

        // execute
        final var failure = catchThrowable(() -> new MavenRuntimeContext(
            MavenRuntimeContext.SCHEMA_VERSION, plan,
            ModulePlanPaths.snapshotsDirectory(root.resolve("sibling")).resolve("snapshot.json").toString(),
            ProcessIdentity.current(), "1".repeat(32)
        ));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("owning module");
    }

    @Test
    void processIdentity_shouldRejectPidReuseWithADifferentStartInstant()
    {
        // setup
        final ProcessIdentity identity = new ProcessIdentity(ProcessHandle.current().pid(), Instant.EPOCH.toString());

        // execute
        final boolean current = identity.matchesCurrentAncestry();

        // verify
        assertThat(current).isFalse();
    }

    @Test
    void runtimeContextPath_shouldTreatExecutionIdsAsOpaqueIdentity(@TempDir Path root)
    {
        // setup
        final String executionId = "../../other-module/paper";

        // execute
        final Path path = ModulePlanPaths.runtimeContext(root, executionId);

        // verify
        assertThat(path.startsWith(root.resolve(".lightkeeper/test-plan/invocations"))).isTrue();
        assertThat(path.getFileName().toString()).matches("[a-f0-9]{64}\\.json");
    }

    @Test
    void modulePlan_shouldRejectUnsupportedSchema(@TempDir Path root)
    {
        // setup
        final MavenBuildContext context = context(root, Map.of());

        // execute
        final var failure = catchThrowable(() -> new ModuleTestPlan(
            ModuleTestPlan.SCHEMA_VERSION + 1, root.toString(), root.resolve("pom.xml").toString(),
            root.resolve("pom.xml").toString(), root.resolve("classes").toString(), List.of("paper"), "paper", context
        ));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schema");
    }

    @Test
    void moduleAnchor_shouldRejectUnsupportedSchema(@TempDir Path root)
    {
        // setup
        final Path output = root.resolve("classes");

        // execute
        final var failure = catchThrowable(() -> new TestModuleAnchor(
            TestModuleAnchor.SCHEMA_VERSION + 1, root.toString(), output.toString()
        ));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schema");
    }

    @Test
    void runtimeContext_shouldRejectUnsupportedSchema(@TempDir Path root)
    {
        // setup
        final EffectiveTestPlan plan = effectivePlan(root, "paper");
        final Path snapshot = ModulePlanPaths.snapshotsDirectory(root).resolve("snapshot/snapshot.json");

        // execute
        final var failure = catchThrowable(() -> new MavenRuntimeContext(
            MavenRuntimeContext.SCHEMA_VERSION + 1, plan, snapshot.toString(), ProcessIdentity.current(), "1".repeat(32)
        ));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schema");
    }

    private static MavenBuildContext context(Path root, Map<String, String> properties)
    {
        return new MavenBuildContext(
            "mvn", root.toString(), List.of("e2e"), List.of(), properties, null, null, null, null
        );
    }

    private static EffectiveTestPlan effectivePlan(Path module, String executionId)
    {
        return new EffectiveTestPlan(
            module.toString(), executionId, "paper", "latest-supported",
            new EffectiveTestPlan.Target(EffectiveTestPlan.TargetKind.SERVER_ONLY, null, null, null, null),
            Map.of("memoryMb", "1024"), List.of()
        );
    }
}
