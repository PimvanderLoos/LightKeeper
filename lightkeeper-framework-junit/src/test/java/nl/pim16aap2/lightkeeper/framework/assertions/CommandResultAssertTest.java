package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.CommandResult;
import org.junit.jupiter.api.Test;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Tests returned command flags and actionable dispatch diagnostics. */
class CommandResultAssertTest
{
    @Test
    void commandAssertions_shouldCheckBothDispatchOutcomes()
    {
        // setup
        final var success = assertThat(new CommandResult(true, "Dispatched"));
        final var failure = assertThat(new CommandResult(false, "Unknown command"));

        // execute
        final var first = success.isSuccessful();
        final var second = failure.isFailure();

        // verify
        assertThat(first).isSameAs(success);
        assertThat(second).isSameAs(failure);
    }

    @Test
    void isSuccessful_shouldIncludeFailureDiagnostic()
    {
        // setup
        final var assertion = assertThat(new CommandResult(false, "Unknown command"));

        // execute
        final Throwable failure = catchThrowable(assertion::isSuccessful);

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("false", "Unknown command");
    }

    @Test
    void isFailure_shouldRejectSuccessfulDispatch()
    {
        // setup
        final var assertion = assertThat(new CommandResult(true, "Dispatched"));

        // execute
        final Throwable failure = catchThrowable(assertion::isFailure);

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContainingAll("true", "Dispatched");
    }

    @Test
    void isSuccessful_shouldRejectNullResult()
    {
        // setup
        final var assertion = new CommandResultAssert(null);

        // execute
        final Throwable failure = catchThrowable(assertion::isSuccessful);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
    }
}
