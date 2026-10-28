package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.IServerControl;
import nl.pim16aap2.lightkeeper.protocol.ServerPluginSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests live lookup, presence semantics, and diagnostic failures for plugin assertions. */
@ExtendWith(MockitoExtension.class)
class ServerPluginAssertTest
{
    /** Server supplying the plugin observations. */
    @Mock
    private IServerControl server;

    /** Verifies that tests leave no unaccounted mock interactions. */
    @AfterEach
    void verifyInteractions()
    {
        verifyNoMoreInteractions(server);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isPresent_shouldAcceptEnabledAndDisabledPlugins(boolean enabled)
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.of(plugin(enabled)));
        final var assertion = new ServerControlAssert(server).plugin("Example");

        // execute
        final var result = assertion.isPresent();

        // verify
        assertThat(result).isSameAs(assertion);

        verify(server).plugin("Example");
    }

    @Test
    void isAbsent_shouldAcceptMissingPlugin()
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.empty());
        final var assertion = new ServerControlAssert(server).plugin("Example");

        // execute
        final var result = assertion.isAbsent();

        // verify
        assertThat(result).isSameAs(assertion);

        verify(server).plugin("Example");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isAbsent_shouldRejectPresentPlugin(boolean enabled)
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.of(plugin(enabled)));

        // execute
        final Throwable failure = catchThrowable(
            () -> new ServerControlAssert(server).plugin("Example").isAbsent()
        );

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("Example", "was present");
    }

    @ParameterizedTest
    @MethodSource("presenceAssertions")
    void pluginAssertions_shouldRejectMissingPlugin(Consumer<ServerPluginAssert> check)
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.empty());
        final var assertion = new ServerControlAssert(server).plugin("Example");

        // execute
        final Throwable failure = catchThrowable(() -> check.accept(assertion));

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("Example", "not present");

        verify(server).plugin("Example");
    }

    @Test
    void isEnabled_shouldAcceptEnabledPlugin()
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.of(plugin(true)));
        final var assertion = new ServerControlAssert(server).plugin("Example");

        // execute
        final var result = assertion.isEnabled();

        // verify
        assertThat(result).isSameAs(assertion);

        verify(server).plugin("Example");
    }

    @Test
    void isEnabled_shouldReportDisabledPlugin()
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.of(plugin(false)));

        // execute
        final Throwable failure = catchThrowable(
            () -> new ServerControlAssert(server)
                .plugin("Example")
                .isEnabled()
        );

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("Example", "to be enabled", "was disabled");
    }

    @Test
    void isDisabled_shouldAcceptDisabledPlugin()
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.of(plugin(false)));
        final var assertion = new ServerControlAssert(server).plugin("Example");

        // execute
        final var result = assertion.isDisabled();

        // verify
        assertThat(result).isSameAs(assertion);

        verify(server).plugin("Example");
    }

    @Test
    void isDisabled_shouldReportEnabledPlugin()
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.of(plugin(true)));

        // execute
        final Throwable failure = catchThrowable(
            () -> new ServerControlAssert(server)
                .plugin("Example")
                .isDisabled()
        );

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("Example", "to be disabled", "was enabled");
    }

    @Test
    void hasVersion_shouldAcceptExactVersion()
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.of(plugin(true)));
        final var assertion = new ServerControlAssert(server).plugin("Example");

        // execute
        final var result = assertion.hasVersion("1.0.0");

        // verify
        assertThat(result).isSameAs(assertion);

        verify(server).plugin("Example");
    }

    @Test
    void hasVersion_shouldCompareExactStringsRatherThanSemanticVersions()
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.of(plugin(true)));

        // execute
        final Throwable failure = catchThrowable(
            () -> new ServerControlAssert(server)
                .plugin("Example")
                .hasVersion("1.0")
        );

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("Example", "'1.0'", "'1.0.0'");
    }

    @Test
    void plugin_shouldNotQueryServerUntilAssertionEvaluation()
    {
        // setup
        final var serverAssertion = new ServerControlAssert(server);

        // execute
        final var assertion = serverAssertion.plugin("Example");

        // verify
        assertThat(assertion).isNotNull();
    }

    @Test
    void pluginAssertions_shouldReadFreshStateOnRetainedSubject()
    {
        // setup
        when(server.plugin("Example")).thenReturn(
            Optional.empty(),
            Optional.of(plugin(false)),
            Optional.of(plugin(true))
        );
        final var assertion = new ServerControlAssert(server).plugin("Example");

        // execute
        assertion
            .isAbsent()
            .isDisabled()
            .isEnabled();

        // verify
        verify(server, times(3)).plugin("Example");
    }

    @Test
    void isEnabled_shouldRetryFreshLookupWithEventually()
    {
        // setup
        when(server.plugin("Example")).thenReturn(Optional.empty(), Optional.of(plugin(true)));
        final var assertion = new ServerControlAssert(server).plugin("Example");

        // execute
        eventually(Duration.ofSeconds(2), assertion::isEnabled);

        // verify
        verify(server, times(2)).plugin("Example");
    }

    @Test
    void isAbsent_shouldPropagateLookupFailure()
    {
        // setup
        final var expected = new IllegalStateException("Framework is closed.");
        when(server.plugin("Example")).thenThrow(expected);

        // execute
        final Throwable failure = catchThrowable(
            () -> new ServerControlAssert(server).plugin("Example").isAbsent()
        );

        // verify
        assertThat(failure).isSameAs(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void plugin_shouldRejectBlankNameBeforeLookup(String name)
    {
        // setup
        final var assertion = new ServerControlAssert(server);

        // execute
        final Throwable failure = catchThrowable(() -> assertion.plugin(name));

        // verify
        assertThat(failure)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pluginName");
    }

    @Test
    @SuppressWarnings("NullAway")
        // Deliberately tests the non-null input boundary.
    void plugin_shouldRejectNullNameBeforeLookup()
    {
        // setup
        final var assertion = new ServerControlAssert(server);

        // execute
        final Throwable failure = catchThrowable(() -> assertion.plugin(null));

        // verify
        assertThat(failure)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("pluginName");
    }

    @Test
    @SuppressWarnings("NullAway")
        // Deliberately tests the non-null input boundary.
    void hasVersion_shouldRejectNullVersionBeforeLookup()
    {
        // setup
        final var assertion = new ServerControlAssert(server).plugin("Example");

        // execute
        final Throwable failure = catchThrowable(() -> assertion.hasVersion(null));

        // verify
        assertThat(failure)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("expectedVersion");
    }

    private static Stream<Consumer<ServerPluginAssert>> presenceAssertions()
    {
        return Stream.of(
            ServerPluginAssert::isPresent,
            ServerPluginAssert::isEnabled,
            ServerPluginAssert::isDisabled,
            assertion -> assertion.hasVersion("1.0.0")
        );
    }

    private static ServerPluginSnapshot plugin(boolean enabled)
    {
        return new ServerPluginSnapshot(
            "Example",
            "1.0.0", "",
            List.of(),
            enabled
        );
    }
}
