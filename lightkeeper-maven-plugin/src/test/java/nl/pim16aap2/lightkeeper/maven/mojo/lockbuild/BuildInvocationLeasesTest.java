package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import nl.pim16aap2.lightkeeper.runtime.BuildLeaseIdentity;
import nl.pim16aap2.lightkeeper.runtime.BuildOutputLease;
import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
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
            BuildInvocationLeases.release(session);
            verify(session).getProjects();
            verifyNoMoreInteractions(session);
        }
        verifyNoMoreInteractions(log);
    }

    @Test
    void acquire_shouldShareOwnershipAcrossTheSameMavenInvocation(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenSession session = session(root);
        BuildInvocationLeases.acquire(session, Duration.ofSeconds(1), log);
        final BuildLeaseIdentity first = owner(root);

        // execute
        BuildInvocationLeases.acquire(session, Duration.ofSeconds(1), log);
        final BuildLeaseIdentity second = owner(root);

        // verify
        assertThat(second).isEqualTo(first);
        final ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(log, times(2)).info(messages.capture());
        assertThat(messages.getAllValues()).noneMatch(message -> message.contains(first.nonce()));
    }

    @Test
    void acquire_shouldCoordinateRootAndModuleSelectionsForSharedOutputs(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path module = Files.createDirectories(root.resolve("module"));
        final MavenSession reactor = session(root, module);
        final MavenSession child = session(module);
        BuildInvocationLeases.acquire(reactor, Duration.ofSeconds(1), log);

        // execute
        final Throwable failure = catchThrowable(() ->
            BuildInvocationLeases.acquire(child, Duration.ofMillis(30), log));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasRootCauseMessage(
            "Timed out waiting for the JVM's file lease: " + BuildOutputLease.lockPath(module)
        );
        assertThat(BuildInvocationLeases.isHeld(reactor)).isTrue();
        verify(log, times(5)).info(anyString());
    }

    @Test
    void acquire_shouldUseTheSameCanonicalOrderForOppositeProjectOrders(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path first = Files.createDirectories(root.resolve("a"));
        final Path last = Files.createDirectories(root.resolve("z"));
        final MavenSession forward = session(first, last);
        final MavenSession reverse = session(last, first);
        BuildInvocationLeases.acquire(forward, Duration.ofSeconds(1), log);

        // execute
        final Throwable failure = catchThrowable(() ->
            BuildInvocationLeases.acquire(reverse, Duration.ofMillis(30), log));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasRootCauseMessage(
            "Timed out waiting for the JVM's file lease: " + BuildOutputLease.lockPath(first)
        );
        verify(log, times(5)).info(anyString());
    }

    @Test
    void acquire_shouldReleasePartialOwnershipWhenAnotherProjectCannotBeAcquired(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path first = Files.createDirectories(root.resolve("a"));
        final Path last = Files.createDirectories(root.resolve("z"));
        final MavenSession busy = session(last);
        final MavenSession waiting = session(first, last);
        BuildInvocationLeases.acquire(busy, Duration.ofSeconds(1), log);

        // execute
        final Throwable failure = catchThrowable(() ->
            BuildInvocationLeases.acquire(waiting, Duration.ofMillis(100), log));

        // verify
        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(BuildInvocationLeases.isHeld(waiting)).isFalse();
        try (var recovered = BuildOutputLease.acquire(first, Duration.ofMillis(100)))
        {
            assertThat(recovered.identity().process().matchesCurrentAncestry()).isTrue();
        }
        verify(log, times(5)).info(anyString());
    }

    @Test
    void acquire_shouldDeduplicateAliasesForOnePhysicalProject(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path actual = Files.createDirectories(root.resolve("actual"));
        final Path alias = Files.createSymbolicLink(root.resolve("alias"), actual);
        final MavenSession session = session(actual, alias);

        // execute
        BuildInvocationLeases.acquire(session, Duration.ofMillis(100), log);

        // verify
        assertThat(owner(actual).process().matchesCurrentAncestry()).isTrue();
        verify(log, times(2)).info(anyString());
    }

    @Test
    void release_shouldAllowTheNextInvocationAfterAllOutputsStopChanging(@TempDir Path root)
        throws Exception
    {
        // setup
        final MavenSession first = session(root);
        final MavenSession second = session(root);
        BuildInvocationLeases.acquire(first, Duration.ofSeconds(1), log);
        final BuildLeaseIdentity previous = owner(root);

        // execute
        BuildInvocationLeases.release(first);
        BuildInvocationLeases.acquire(second, Duration.ofSeconds(1), log);

        // verify
        assertThat(owner(root).nonce()).isNotEqualTo(previous.nonce());
        assertThat(BuildInvocationLeases.isHeld(first)).isFalse();
        verify(log, times(4)).info(anyString());
    }

    private static BuildLeaseIdentity owner(Path module)
        throws Exception
    {
        return GeneratedMetadata.read(BuildOutputLease.lockPath(module).resolveSibling("owner.json"),
            BuildLeaseIdentity.class);
    }

    private MavenSession session(Path... modules)
    {
        final List<MavenProject> projects = new ArrayList<>();
        for (final Path module : modules)
        {
            final MavenProject project = new MavenProject();
            project.setFile(module.resolve("pom.xml").toFile());
            projects.add(project);
        }
        final MavenSession session = mock();
        when(session.getProjects()).thenReturn(projects);
        sessions.add(session);
        return session;
    }
}
