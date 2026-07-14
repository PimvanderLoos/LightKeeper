package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LightkeeperExtensionDiagnosticsTest
{
    private static final String MODE_PROPERTY = "lightkeeper.diagnostics";

    @Test
    void diagnosticsMode_shouldParseAllKnownValuesAndDefaultBlankToOnFailure()
    {
        // setup
        final String previousValue = System.getProperty(MODE_PROPERTY);
        try
        {
            // execute + verify
            System.clearProperty(MODE_PROPERTY);
            assertThat(LightkeeperExtension.diagnosticsMode())
                .isEqualTo(LightkeeperExtension.DiagnosticsMode.ON_FAILURE);

            System.setProperty(MODE_PROPERTY, "");
            assertThat(LightkeeperExtension.diagnosticsMode())
                .isEqualTo(LightkeeperExtension.DiagnosticsMode.ON_FAILURE);

            System.setProperty(MODE_PROPERTY, "on-failure");
            assertThat(LightkeeperExtension.diagnosticsMode())
                .isEqualTo(LightkeeperExtension.DiagnosticsMode.ON_FAILURE);

            System.setProperty(MODE_PROPERTY, "ALWAYS");
            assertThat(LightkeeperExtension.diagnosticsMode())
                .isEqualTo(LightkeeperExtension.DiagnosticsMode.ALWAYS);

            System.setProperty(MODE_PROPERTY, "off");
            assertThat(LightkeeperExtension.diagnosticsMode())
                .isEqualTo(LightkeeperExtension.DiagnosticsMode.OFF);
        }
        finally
        {
            restore(previousValue);
        }
    }

    @Test
    void diagnosticsMode_shouldRejectUnknownValues()
    {
        // setup
        final String previousValue = System.getProperty(MODE_PROPERTY);
        try
        {
            System.setProperty(MODE_PROPERTY, "on_failure");

            // execute + verify
            assertThatThrownBy(LightkeeperExtension::diagnosticsMode)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("on_failure")
                .hasMessageContaining("lightkeeper.diagnostics");
        }
        finally
        {
            restore(previousValue);
        }
    }

    @Test
    void shouldWriteBundle_shouldWriteOnlyRealFailuresInOnFailureMode()
    {
        // setup
        final Throwable realFailure = new AssertionError("boom");
        final Throwable assumptionAbort = new TestAbortedException("skipped on this platform");

        // execute + verify
        assertThat(LightkeeperExtension.shouldWriteBundle(
            LightkeeperExtension.DiagnosticsMode.ON_FAILURE, realFailure)).isTrue();
        assertThat(LightkeeperExtension.shouldWriteBundle(
            LightkeeperExtension.DiagnosticsMode.ON_FAILURE, null)).isFalse();
        assertThat(LightkeeperExtension.shouldWriteBundle(
            LightkeeperExtension.DiagnosticsMode.ON_FAILURE, assumptionAbort)).isFalse();
    }

    @Test
    void shouldWriteBundle_shouldFollowModeForAlwaysAndOff()
    {
        // setup
        final Throwable realFailure = new AssertionError("boom");

        // execute + verify
        assertThat(LightkeeperExtension.shouldWriteBundle(
            LightkeeperExtension.DiagnosticsMode.ALWAYS, null)).isTrue();
        assertThat(LightkeeperExtension.shouldWriteBundle(
            LightkeeperExtension.DiagnosticsMode.ALWAYS, realFailure)).isTrue();
        assertThat(LightkeeperExtension.shouldWriteBundle(
            LightkeeperExtension.DiagnosticsMode.OFF, realFailure)).isFalse();
        assertThat(LightkeeperExtension.shouldWriteBundle(
            LightkeeperExtension.DiagnosticsMode.OFF, null)).isFalse();
    }

    private static void restore(String previousValue)
    {
        if (previousValue == null)
            System.clearProperty(MODE_PROPERTY);
        else
            System.setProperty(MODE_PROPERTY, previousValue);
    }
}
