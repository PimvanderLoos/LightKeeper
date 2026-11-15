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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
    void prepare_shouldTerminateItsProducerAfterTimeout()
        throws Exception
    {
        // setup
        final Path executable = sleepingProducer();
        final ModuleTestPlan plan = plan(executable.toString(), List.of("default"),
            Map.of("lightkeeper.buildTimeoutSeconds", "1"));

        // execute
        final Throwable failure = catchThrowable(() -> NativeMavenPreparation.prepare(plan));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("timed out after 1");
        assertThat(producerIsAlive()).isFalse();
    }

    @Test
    void prepare_shouldRetainInterruptionAndTerminateItsProducer()
        throws Exception
    {
        // setup
        final ModuleTestPlan plan = plan(sleepingProducer().toString(), List.of("default"));
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicBoolean interrupted = new AtomicBoolean();
        final Thread worker = Thread.ofPlatform().unstarted(() ->
        {
            failure.set(catchThrowable(() -> NativeMavenPreparation.prepare(plan)));
            interrupted.set(Thread.currentThread().isInterrupted());
        });

        // execute
        worker.start();
        final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!Files.isRegularFile(root.resolve("child.pid")) && System.nanoTime() < deadline)
        {
            Thread.sleep(10);
        }
        worker.interrupt();
        worker.join(5_000);

        // verify
        assertThat(worker.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(IllegalStateException.class).hasMessageContaining("cancelled");
        assertThat(interrupted).isTrue();
        assertThat(producerIsAlive()).isFalse();
    }

    @Test
    void prepare_shouldAwaitForcedTerminationBeforeRestoringInterruption()
        throws Exception
    {
        // setup
        final ModuleTestPlan plan = plan("mvn", List.of("default"));
        final Process child = mock();
        final AtomicInteger waits = new AtomicInteger();
        when(child.getOutputStream()).thenReturn(OutputStream.nullOutputStream());
        when(child.isAlive()).thenReturn(true);
        when(child.descendants()).thenAnswer(ignored -> Stream.empty());
        when(child.waitFor(anyLong(), any(TimeUnit.class))).thenAnswer(ignored ->
        {
            final int attempt = waits.incrementAndGet();
            if (attempt == 1)
            {
                throw new InterruptedException("cancelled producer wait");
            }
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            if (attempt == 2)
            {
                throw new InterruptedException("interrupted again during cleanup");
            }
            return true;
        });
        try (var builders = mockConstruction(ProcessBuilder.class, (builder, context) ->
        {
            when(builder.environment()).thenReturn(new HashMap<>());
            when(builder.start()).thenReturn(child);
        }); var reaper = mockStatic(OrphanReaperLauncher.class))
        {
            // execute
            final Throwable failure;
            final boolean interrupted;
            try
            {
                failure = catchThrowable(() -> NativeMavenPreparation.prepare(plan));
                interrupted = Thread.currentThread().isInterrupted();
            }
            finally
            {
                Thread.interrupted();
            }

            // verify
            assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("cancelled");
            assertThat(interrupted).isTrue();
            assertThat(waits).hasValue(3);
            assertThat(builders.constructed()).hasSize(1);
            verify(child).destroyForcibly();
            reaper.verify(() -> OrphanReaperLauncher.launch(eq(ProcessHandle.current().pid()), eq(0L)));
        }
    }

    @Test
    void prepare_shouldReevaluateAnExplicitVariantAddedAfterTheCachedPlan()
        throws Exception
    {
        // setup
        final Path executable = Files.writeString(root.resolve("maven"), "#!/bin/sh\nexit 17\n");
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        final ModuleTestPlan cached = plan(executable.toString(), List.of("old-default"));
        System.setProperty(NativeMavenPreparation.EXECUTION_PROPERTY, "new-execution");

        // execute
        final Throwable failure = catchThrowable(() -> NativeMavenPreparation.prepare(cached));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("exit code 17", "maven.log");
    }

    @Test
    void prepare_shouldReevaluateAnAmbiguousCachedPlanBeforeSelectingTheCurrentDefault()
        throws Exception
    {
        // setup
        final Path executable = Files.writeString(root.resolve("maven"), "#!/bin/sh\nexit 17\n");
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        final ModuleTestPlan cached = plan(executable.toString(), List.of("old-paper", "old-spigot"));

        // execute
        final Throwable failure = catchThrowable(() -> NativeMavenPreparation.prepare(cached));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("exit code 17", "maven.log");
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
        return plan(executable, executions, Map.of("revision", "2"));
    }

    private Path sleepingProducer()
        throws Exception
    {
        final Path executable = Files.writeString(root.resolve("sleeping-maven"),
            "#!/bin/sh\necho $$ > child.pid\nexec sleep 30\n");
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        return executable;
    }

    private boolean producerIsAlive()
        throws Exception
    {
        final long pid = Long.parseLong(Files.readString(root.resolve("child.pid")).trim());
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private ModuleTestPlan plan(String executable, List<String> executions, Map<String, String> properties)
    {
        return new ModuleTestPlan(
            ModuleTestPlan.SCHEMA_VERSION, root.toString(), root.resolve("pom.xml").toString(),
            root.resolve("pom.xml").toString(), root.resolve("custom/test-output").toString(), executions,
            executions.size() == 1 ? executions.getFirst() : null,
            new MavenBuildContext(executable, System.getProperty("java.home"), List.of("integration"),
                List.of("release"), properties, null, null, null, null)
        );
    }
}
