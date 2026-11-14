package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import nl.pim16aap2.lightkeeper.runtime.BuildOutputLease;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuildInvocationLeasesTest
{
    @Mock
    private Log log;
    private final List<MavenSession> sessions = new ArrayList<>();

    @AfterEach
    void releaseLeasesAndVerifyInteractions()
    {
        for (final MavenSession session : sessions)
        {
            BuildInvocationLeases.finishInvocation(session);
            verifyNoMoreInteractions(session);
        }
        verifyNoMoreInteractions(log);
    }

    @Test
    void acquire_shouldShareOneLeaseAcrossTheSameMavenInvocation(@TempDir Path root)
    {
        // setup
        final MavenSession session = session(root);

        // execute
        final var first = BuildInvocationLeases.acquire(session, Duration.ofSeconds(1), log);
        final var second = BuildInvocationLeases.acquire(session, Duration.ofSeconds(1), log);

        // verify
        assertThat(second).isSameAs(first);
        final ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(log, times(2)).info(messages.capture());
        assertThat(messages.getAllValues()).noneMatch(message -> message.contains(first.nonce()));
        verify(session).getExecutionRootDirectory();
    }

    @Test
    void acquire_shouldCoordinateSeparateMavenInvocations(@TempDir Path root)
    {
        // setup
        final MavenSession first = session(root);
        final MavenSession second = session(root);
        BuildInvocationLeases.acquire(first, Duration.ofSeconds(1), log);

        // execute
        final var failure = catchThrowable(() -> BuildInvocationLeases.acquire(second, Duration.ofMillis(30), log));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasRootCauseMessage(
            "Timed out waiting for the JVM's file lease: " + BuildOutputLease.lockPath(root)
        );
        verify(log, times(3)).info(anyString());
        verify(first).getExecutionRootDirectory();
        verify(second).getExecutionRootDirectory();
    }

    @Test
    void release_shouldAllowTheNextModuleToAcquireANewLease(@TempDir Path root)
    {
        // setup
        final MavenSession session = session(root);
        final var first = BuildInvocationLeases.acquire(session, Duration.ofSeconds(1), log);

        // execute
        BuildInvocationLeases.release(session);
        final var second = BuildInvocationLeases.acquire(session, Duration.ofSeconds(1), log);

        // verify
        assertThat(second.nonce()).isNotEqualTo(first.nonce());
        verify(log, times(4)).info(anyString());
        verify(session, times(2)).getExecutionRootDirectory();
        verify(session).isParallel();
    }

    @Test
    void releaseAfterFailure_shouldReleaseOutputsWithoutReplacingThePreparationFailure(@TempDir Path root)
    {
        // setup
        final MavenSession session = session(root);
        final var failure = new IllegalStateException("preparation failed");
        BuildInvocationLeases.acquire(session, Duration.ofSeconds(1), log);

        // execute
        BuildInvocationLeases.releaseAfterFailure(session, failure);

        // verify
        assertThat(failure).hasMessage("preparation failed").hasNoSuppressedExceptions();
        assertThat(BuildOutputLease.lockPath(root).resolveSibling("owner.json")).doesNotExist();
        verify(log, times(2)).info(anyString());
        verify(session).getExecutionRootDirectory();
        verify(session).isParallel();
    }

    @Test
    void release_shouldRetainParallelProducerOwnershipUntilInvocationExit(@TempDir Path root)
    {
        // setup
        final MavenSession session = session(root);
        when(session.isParallel()).thenReturn(true);
        BuildInvocationLeases.acquire(session, Duration.ofSeconds(1), log);

        // execute
        BuildInvocationLeases.release(session);
        final Throwable competing = catchThrowable(() -> BuildOutputLease.acquire(root, Duration.ofMillis(30)));
        BuildInvocationLeases.finishInvocation(session);

        // verify
        assertThat(competing).isInstanceOf(IllegalStateException.class).hasRootCauseMessage(
            "Timed out waiting for the JVM's file lease: " + BuildOutputLease.lockPath(root)
        );
        assertThat(BuildInvocationLeases.isHeld(session)).isFalse();
        try (var next = BuildOutputLease.acquire(root, Duration.ofMillis(100)))
        {
            assertThat(next.identity().process().matchesCurrentAncestry()).isTrue();
        }
        verify(session).getExecutionRootDirectory();
        verify(session).isParallel();
        verify(log, times(2)).info(anyString());
    }

    @Test
    void releaseAfterFailure_shouldProtectOtherModulesStillRunningInParallel(@TempDir Path root)
    {
        // setup
        final MavenSession session = session(root);
        when(session.isParallel()).thenReturn(true);
        final var failure = new IllegalStateException("first module failed");
        BuildInvocationLeases.acquire(session, Duration.ofSeconds(1), log);

        // execute
        BuildInvocationLeases.releaseAfterFailure(session, failure);

        // verify
        assertThat(failure).hasMessage("first module failed").hasNoSuppressedExceptions();
        assertThat(BuildInvocationLeases.isHeld(session)).isTrue();
        verify(session).getExecutionRootDirectory();
        verify(session).isParallel();
        verify(log, times(2)).info(anyString());
    }

    private MavenSession session(Path root)
    {
        final MavenSession session = mock();
        when(session.getExecutionRootDirectory()).thenReturn(root.toString());
        sessions.add(session);
        return session;
    }
}
