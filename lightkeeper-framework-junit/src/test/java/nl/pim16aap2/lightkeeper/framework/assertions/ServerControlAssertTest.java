package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.IServerControl;
import nl.pim16aap2.lightkeeper.framework.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Tests framework-to-server navigation and shared server observations. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ServerControlAssertTest
{
    /** Framework providing the server facet. */
    @Mock
    private ILightkeeperFramework framework;

    /** Server supplying current platform and output. */
    @Mock
    private IServerControl server;

    @Test
    void server_shouldNavigateWithoutReadingServerState()
    {
        // setup
        when(framework.server()).thenReturn(server);

        // execute
        final var assertion = assertThat(framework).server();

        // verify
        assertThat(assertion).isNotNull();
        verifyNoInteractions(server);
    }

    @Test
    void server_shouldRejectNullFramework()
    {
        // setup
        final var assertion = new LightkeeperFrameworkAssert(null);

        // execute
        final Throwable failure = catchThrowable(assertion::server);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
        verifyNoInteractions(server);
    }

    @Test
    void isPaper_shouldReadPlatformOnce()
    {
        // setup
        when(framework.server()).thenReturn(server);
        when(server.platform()).thenReturn(Platform.PAPER);
        final var assertion = assertThat(framework).server();

        // execute
        final var result = assertion.isPaper();

        // verify
        assertThat(result).isSameAs(assertion);
        verify(server).platform();
        verifyNoMoreInteractions(server);
    }

    @Test
    void isSpigot_shouldReadPlatformOnce()
    {
        // setup
        when(server.platform()).thenReturn(Platform.SPIGOT);
        final var assertion = new ServerControlAssert(server);

        // execute
        final var result = assertion.isSpigot();

        // verify
        assertThat(result).isSameAs(assertion);
        verify(server).platform();
        verifyNoMoreInteractions(server);
    }

    @Test
    void isPaper_shouldReportObservedPlatformFromOneRead()
    {
        // setup
        when(server.platform()).thenReturn(Platform.UNKNOWN);

        // execute
        final Throwable failure = catchThrowable(() -> new ServerControlAssert(server).isPaper());

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContainingAll("PAPER", "UNKNOWN");
        verify(server).platform();
        verifyNoMoreInteractions(server);
    }

    @Test
    void serverOutput_shouldShareNavigationWithFrameworkAssertion()
    {
        // setup
        when(framework.server()).thenReturn(server);
        when(server.output()).thenReturn(List.of("Started"), List.of("Started", "Done"));

        // execute
        final var earlier = assertThat(framework).server().output();
        final var later = assertThat(framework).serverOutput();

        // verify
        earlier.containsExactly("Started");
        later.containsExactly("Started", "Done");
        verify(server, times(2)).output();
        verifyNoMoreInteractions(server);
    }

    @Test
    void isPaper_shouldRejectNullServer()
    {
        // setup
        final var assertion = new ServerControlAssert(null);

        // execute
        final Throwable failure = catchThrowable(assertion::isPaper);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
    }

    @Test
    void isPaper_shouldPreserveFrameworkAssertionDescription()
    {
        // setup
        when(framework.server()).thenReturn(server);
        when(server.platform()).thenReturn(Platform.SPIGOT);

        // execute
        final Throwable failure = catchThrowable(() -> assertThat(framework).as("Startup check").isPaper());

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("Startup check", "PAPER", "SPIGOT");
    }

    @Test
    void isPaper_shouldPreserveFrameworkCustomFailureMessage()
    {
        // setup
        when(framework.server()).thenReturn(server);
        when(server.platform()).thenReturn(Platform.SPIGOT);

        // execute
        final Throwable failure = catchThrowable(() ->
            assertThat(framework).overridingErrorMessage("Wrong startup platform").isPaper());

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class).hasMessage("Wrong startup platform");
    }

    @Test
    void plugin_shouldPreserveDescriptionThroughBothNavigationSteps()
    {
        // setup
        when(framework.server()).thenReturn(server);
        when(server.plugin("Missing")).thenReturn(java.util.Optional.empty());

        // execute
        final Throwable failure = catchThrowable(() ->
            assertThat(framework).as("Plugin startup").server().plugin("Missing").isEnabled());

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("Plugin startup", "Missing", "not present");
    }
}
