package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension.DiagnosticsMode;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
            // execute
            System.clearProperty(MODE_PROPERTY);
            final DiagnosticsMode whenAbsent = LightkeeperExtension.diagnosticsMode();
            System.setProperty(MODE_PROPERTY, "");
            final DiagnosticsMode whenBlank = LightkeeperExtension.diagnosticsMode();
            System.setProperty(MODE_PROPERTY, "on-failure");
            final DiagnosticsMode whenOnFailure = LightkeeperExtension.diagnosticsMode();
            System.setProperty(MODE_PROPERTY, "ALWAYS");
            final DiagnosticsMode whenAlwaysUpperCase = LightkeeperExtension.diagnosticsMode();
            System.setProperty(MODE_PROPERTY, "off");
            final DiagnosticsMode whenOff = LightkeeperExtension.diagnosticsMode();

            // verify
            assertThat(whenAbsent).isEqualTo(DiagnosticsMode.ON_FAILURE);
            assertThat(whenBlank).isEqualTo(DiagnosticsMode.ON_FAILURE);
            assertThat(whenOnFailure).isEqualTo(DiagnosticsMode.ON_FAILURE);
            assertThat(whenAlwaysUpperCase).isEqualTo(DiagnosticsMode.ALWAYS);
            assertThat(whenOff).isEqualTo(DiagnosticsMode.OFF);
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

            // execute
            final Throwable thrown = catchThrowable(LightkeeperExtension::diagnosticsMode);

            // verify
            assertThat(thrown)
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

        // execute
        final boolean writesRealFailure =
            LightkeeperExtension.shouldWriteBundle(DiagnosticsMode.ON_FAILURE, realFailure);
        final boolean writesOnPass = LightkeeperExtension.shouldWriteBundle(DiagnosticsMode.ON_FAILURE, null);
        final boolean writesOnAbort =
            LightkeeperExtension.shouldWriteBundle(DiagnosticsMode.ON_FAILURE, assumptionAbort);

        // verify
        assertThat(writesRealFailure).isTrue();
        assertThat(writesOnPass).isFalse();
        assertThat(writesOnAbort).isFalse();
    }

    @Test
    void shouldWriteBundle_shouldFollowModeForAlwaysAndOff()
    {
        // setup
        final Throwable realFailure = new AssertionError("boom");

        // execute
        final boolean alwaysWritesOnPass = LightkeeperExtension.shouldWriteBundle(DiagnosticsMode.ALWAYS, null);
        final boolean alwaysWritesOnFailure =
            LightkeeperExtension.shouldWriteBundle(DiagnosticsMode.ALWAYS, realFailure);
        final boolean offWritesOnFailure = LightkeeperExtension.shouldWriteBundle(DiagnosticsMode.OFF, realFailure);
        final boolean offWritesOnPass = LightkeeperExtension.shouldWriteBundle(DiagnosticsMode.OFF, null);

        // verify
        assertThat(alwaysWritesOnPass).isTrue();
        assertThat(alwaysWritesOnFailure).isTrue();
        assertThat(offWritesOnFailure).isFalse();
        assertThat(offWritesOnPass).isFalse();
    }

    private static void restore(String previousValue)
    {
        if (previousValue == null)
            System.clearProperty(MODE_PROPERTY);
        else
            System.setProperty(MODE_PROPERTY, previousValue);
    }
}
