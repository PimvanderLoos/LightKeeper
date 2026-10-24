package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.InteractionResult;
import org.junit.jupiter.api.Test;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Tests that cancellation assertions never infer an event from a false cancellation flag. */
class InteractionResultAssertTest
{
    @Test
    void cancellationAssertions_shouldAcceptMatchingFiredEvents()
    {
        // setup
        final var cancelled = assertThat(new InteractionResult(true, true));
        final var uncancelled = assertThat(new InteractionResult(true, false));

        // execute
        final var first = cancelled
            .hasFiredEvent()
            .isCancelled();
        final var second = uncancelled
            .hasFiredEvent()
            .isNotCancelled();

        // verify
        assertThat(first).isSameAs(cancelled);
        assertThat(second).isSameAs(uncancelled);
    }

    @Test
    void isNotCancelled_shouldRequireAnActualEvent()
    {
        // setup
        final var assertion = assertThat(new InteractionResult(false, false));

        // execute
        final Throwable failure = catchThrowable(assertion::isNotCancelled);

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("no event was fired");
    }

    @Test
    void cancellationAssertions_shouldReportWrongState()
    {
        // setup
        final var cancelled = assertThat(new InteractionResult(true, true));
        final var uncancelled = assertThat(new InteractionResult(true, false));

        // execute
        final Throwable first = catchThrowable(cancelled::isNotCancelled);
        final Throwable second = catchThrowable(uncancelled::isCancelled);

        // verify
        assertThat(first).isInstanceOf(AssertionError.class).hasMessageContaining("was true");
        assertThat(second).isInstanceOf(AssertionError.class).hasMessageContaining("was false");
    }

    @Test
    void hasFiredEvent_shouldRejectNullResult()
    {
        // setup
        final var assertion = new InteractionResultAssert(null);

        // execute
        final Throwable failure = catchThrowable(assertion::hasFiredEvent);

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
    }
}
