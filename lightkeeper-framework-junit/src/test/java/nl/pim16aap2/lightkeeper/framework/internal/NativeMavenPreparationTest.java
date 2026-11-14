package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.provisioning.session.ServerSnapshots;
import nl.pim16aap2.lightkeeper.runtime.BuildOutputLease;
import nl.pim16aap2.lightkeeper.runtime.EffectiveTestPlan;
import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.MavenBuildContext;
import nl.pim16aap2.lightkeeper.runtime.MavenRuntimeContext;
import nl.pim16aap2.lightkeeper.runtime.ModulePlanPaths;
import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;
import nl.pim16aap2.lightkeeper.runtime.ProcessIdentity;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class NativeMavenPreparationTest
{
    @TempDir
    private Path root;

    @AfterEach
    void clearExecutionSelection()
    {
        System.clearProperty(NativeMavenPreparation.EXECUTION_PROPERTY);
    }

    @Test
    void command_shouldPackageTheSelectedReactorWithRetainedSettingsAndNoRecursiveTests()
    {
        // setup
        final ModuleTestPlan plan = plan("mvn", List.of("default"));

        // execute
        final List<String> command = NativeMavenPreparation.command(plan);

        // verify
        assertThat(command).containsSubsequence("-f", root.resolve("pom.xml").toString(), "-pl", ".", "-am");
        assertThat(command).contains("-Pintegration,!release", "-Drevision=2", "-DskipTests=true", "-DskipITs=true");
        assertThat(command.getLast()).isEqualTo("pre-integration-test");
        assertThat(command).doesNotContain("install", "clean", "verify");
    }

    @Test
    void selectedExecution_shouldRejectAmbiguityAndUnknownExplicitSelections()
    {
        // setup
        final ModuleTestPlan plan = plan("mvn", List.of("paper", "spigot"));

        // execute
        final Throwable ambiguous = catchThrowable(() -> NativeMavenPreparation.selectedExecution(plan));
        System.setProperty(NativeMavenPreparation.EXECUTION_PROPERTY, "unknown");
        final Throwable unknown = catchThrowable(() -> NativeMavenPreparation.selectedExecution(plan));

        // verify
        assertThat(ambiguous).isInstanceOf(IllegalStateException.class).hasMessageContaining("paper", "spigot");
        assertThat(unknown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown");
    }

    @Test
    void selectedExecution_shouldRespectExplicitVariant()
    {
        // setup
        final ModuleTestPlan plan = plan("mvn", List.of("paper", "spigot"));
        System.setProperty(NativeMavenPreparation.EXECUTION_PROPERTY, "spigot");

        // execute
        final String selected = NativeMavenPreparation.selectedExecution(plan);

        // verify
        assertThat(selected).isEqualTo("spigot");
    }

    @Test
    void prepare_shouldConsumeTheLiveMavenSnapshotWithoutSpawningABuild()
        throws Exception
    {
        // setup
        final ModuleTestPlan plan = plan("/missing/maven-must-not-run", List.of("default"));
        final Path server = Files.createDirectories(root.resolve("prepared"));
        final Path jar = Files.writeString(server.resolve("server.jar"), "actual-packaged-bytes");
        final RuntimeManifest manifest = new RuntimeManifest(
            "paper", "1.21.11", 132, "cache", server.toString(), jar.toString(), 1024,
            root.resolve("s/agent.sock").toString(), "token", null, null, RuntimeProtocol.VERSION,
            "agent", null, List.of()
        );
        final Path metadata = ServerSnapshots.capture(manifest, ModulePlanPaths.snapshotsDirectory(root));
        final MavenRuntimeContext context = new MavenRuntimeContext(
            MavenRuntimeContext.SCHEMA_VERSION,
            new EffectiveTestPlan(root.toString(), "default", "paper", "latest-supported",
                new EffectiveTestPlan.Target(EffectiveTestPlan.TargetKind.SERVER_ONLY, null, null, null, null),
                Map.of(), List.of()), metadata.toString(), ProcessIdentity.current(), "a".repeat(32)
        );
        GeneratedMetadata.write(context, ModulePlanPaths.runtimeContext(root, "default"));

        // execute
        try (PreparedTestRuntime runtime = NativeMavenPreparation.prepare(plan))
        {
            // verify
            assertThat(Path.of(runtime.snapshot().manifest().serverJar())).hasContent("actual-packaged-bytes");
            assertThat(runtime.moduleDirectory()).isEqualTo(root);
        }
        assertThat(metadata).exists();
    }

    @Test
    void prepare_shouldRejectSurefireBeforePackagingInsteadOfStartingANestedBuild()
    {
        // setup
        final ModuleTestPlan plan = plan("/missing/maven-must-not-run", List.of("default"));
        try (var lease = BuildOutputLease.acquire(root, Duration.ofSeconds(1)))
        {
            // execute
            final Throwable failure = catchThrowable(() -> NativeMavenPreparation.prepare(plan));

            // verify
            assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("Failsafe", "verify");
            assertThat(lease.identity().process().matchesCurrentAncestry()).isTrue();
            assertThat(root.resolve(".lightkeeper/launches")).doesNotExist();
        }
    }

    @Test
    void prepare_shouldFailWithItsBuildLogInsteadOfFallingBackToOldInputs()
        throws Exception
    {
        // setup
        final Path executable = Files.writeString(root.resolve("failing-maven"), "#!/bin/sh\nexit 17\n");
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        final ModuleTestPlan plan = plan(executable.toString(), List.of("default"));

        // execute
        final Throwable failure = catchThrowable(() -> NativeMavenPreparation.prepare(plan));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exit code 17", "maven.log", "No previous runtime");
        try (var launches = Files.list(root.resolve(".lightkeeper/launches")))
        {
            assertThat(launches.map(path -> path.resolve("maven.log"))).allMatch(Files::isRegularFile);
        }
    }

    private ModuleTestPlan plan(String executable, List<String> executions)
    {
        return new ModuleTestPlan(
            ModuleTestPlan.SCHEMA_VERSION, root.toString(), root.resolve("pom.xml").toString(),
            root.resolve("pom.xml").toString(), root.resolve("custom/test-output").toString(), executions,
            executions.size() == 1 ? executions.getFirst() : null,
            new MavenBuildContext(executable, System.getProperty("java.home"), List.of("integration"),
                List.of("release"), Map.of("revision", "2"), null, null, null, null)
        );
    }
}
