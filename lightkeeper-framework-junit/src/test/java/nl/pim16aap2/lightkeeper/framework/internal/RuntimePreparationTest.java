package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.runtime.MavenBuildContext;
import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuntimePreparationTest
{
    @Mock
    private ModuleTestPlan plan;
    @Mock
    private PreparedTestRuntime runtime;

    @AfterEach
    void verifyRemainingInteractions()
    {
        verifyNoMoreInteractions(plan, runtime);
    }

    @Test
    void await_shouldShareSuccessfulInputsUntilRootStoreCleanup()
    {
        // setup
        try (var factory = mockStatic(NativeMavenPreparation.class))
        {
            factory.when(() -> NativeMavenPreparation.prepare(plan)).thenReturn(runtime);

            // execute
            final PreparedTestRuntime first;
            final PreparedTestRuntime second;
            try (var preparation = new RuntimePreparation(plan))
            {
                first = preparation.await();
                second = preparation.await();
                verifyNoInteractions(runtime);
            }

            // verify
            assertThat(first).isSameAs(runtime);
            assertThat(second).isSameAs(first);
            verify(runtime).close();
            factory.verify(() -> NativeMavenPreparation.prepare(plan));
            factory.verifyNoMoreInteractions();
        }
    }

    @Test
    void await_shouldRetainTheOriginalPreparationFailureWithoutRetrying()
    {
        // setup
        final var failure = new IllegalStateException("producer compile failed");
        try (var factory = mockStatic(NativeMavenPreparation.class))
        {
            factory.when(() -> NativeMavenPreparation.prepare(plan)).thenThrow(failure);

            // execute
            final Throwable first;
            final Throwable second;
            try (var preparation = new RuntimePreparation(plan))
            {
                first = catchThrowable(preparation::await);
                second = catchThrowable(preparation::await);
            }

            // verify
            assertThat(first).isSameAs(failure);
            assertThat(second).isSameAs(first);
            factory.verify(() -> NativeMavenPreparation.prepare(plan));
            factory.verifyNoMoreInteractions();
        }
    }

    @Test
    void await_shouldPreserveFatalPreparationErrors()
    {
        // setup
        final var failure = new AssertionError("infrastructure failed");
        try (var factory = mockStatic(NativeMavenPreparation.class))
        {
            factory.when(() -> NativeMavenPreparation.prepare(plan)).thenThrow(failure);

            // execute
            final Throwable result;
            try (var preparation = new RuntimePreparation(plan))
            {
                result = catchThrowable(preparation::await);
            }

            // verify
            assertThat(result).isSameAs(failure);
            factory.verify(() -> NativeMavenPreparation.prepare(plan));
            factory.verifyNoMoreInteractions();
        }
    }

    @Test
    void await_shouldRunOneFailingProducerForConcurrentClasses(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path executable = Files.writeString(root.resolve("producer"),
            "#!/bin/sh\necho attempt >> attempts\nsleep 0.1\nexit 17\n");
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        final var actualPlan = new ModuleTestPlan(
            ModuleTestPlan.SCHEMA_VERSION, root.toString(), root.resolve("pom.xml").toString(),
            root.resolve("pom.xml").toString(), root.resolve("test-classes").toString(), List.of("default"), "default",
            new MavenBuildContext(executable.toString(), System.getProperty("java.home"), List.of(), List.of(),
                Map.of(), null, null, null, null)
        );
        try (var workers = Executors.newFixedThreadPool(2); var preparation = new RuntimePreparation(actualPlan))
        {
            // execute
            final var first = workers.submit(() -> catchThrowable(preparation::await));
            final var second = workers.submit(() -> catchThrowable(preparation::await));
            final Throwable firstFailure = first.get(5, TimeUnit.SECONDS);
            final Throwable secondFailure = second.get(5, TimeUnit.SECONDS);

            // verify
            assertThat(firstFailure).hasMessageContaining("exit code 17");
            assertThat(secondFailure).isSameAs(firstFailure);
            assertThat(Files.readAllLines(root.resolve("attempts"))).containsExactly("attempt");
        }
    }
}
