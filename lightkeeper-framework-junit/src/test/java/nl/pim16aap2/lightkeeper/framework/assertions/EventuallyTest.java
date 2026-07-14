package nl.pim16aap2.lightkeeper.framework.assertions;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class EventuallyTest
{
    @Test
    void eventually_shouldPassImmediatelyWhenAssertionSucceedsOnFirstAttempt()
    {
        // setup
        final AtomicInteger attempts = new AtomicInteger(0);
        final long startNanos = System.nanoTime();

        // execute
        LightkeeperAssertions.eventually(Duration.ofSeconds(5), attempts::incrementAndGet);
        final long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        // verify — a first-attempt success must not burn the 50 ms retry poll interval
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(elapsedMillis).isLessThan(50L);
    }

    @Test
    void eventually_shouldPassAfterRetryingFailedAttempts()
    {
        // setup
        final AtomicInteger attempts = new AtomicInteger(0);

        // execute
        LightkeeperAssertions.eventually(Duration.ofSeconds(5), () ->
        {
            if (attempts.incrementAndGet() < 3)
                throw new AssertionError("Not ready yet.");
        });

        // verify
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void eventually_shouldThrowAssertionErrorWithLastFailureAsCauseWhenTimeoutIsExceeded()
    {
        // setup
        final AtomicInteger attempts = new AtomicInteger(0);

        // execute
        final Throwable thrown = catchThrowable(() -> LightkeeperAssertions.eventually(
            Duration.ofMillis(200), () ->
            {
                attempts.incrementAndGet();
                throw new AssertionError("Still failing.");
            }));

        // verify
        assertThat(thrown)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("did not pass within")
            .hasMessageContaining("attempts");
        assertThat(thrown.getCause())
            .isInstanceOf(AssertionError.class)
            .hasMessage("Still failing.");
        assertThat(attempts.get()).isGreaterThan(0);
    }

    @Test
    void eventually_shouldThrowIllegalStateExceptionImmediatelyWhenNonAssertionExceptionIsThrown()
    {
        // setup
        final AtomicInteger attempts = new AtomicInteger(0);

        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.eventually(Duration.ofSeconds(5), () ->
        {
            attempts.incrementAndGet();
            throw new IllegalStateException("Framework is closed.");
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non-assertion");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void eventually_shouldThrowIllegalStateExceptionWhenNestedInsideAnotherEventually()
    {
        // execute
        final Throwable thrown = catchThrowable(() -> LightkeeperAssertions.eventually(
            Duration.ofSeconds(5), () -> LightkeeperAssertions.eventually(Duration.ofSeconds(5), () -> { })));

        // verify — the nesting guard fires from the inner call and surfaces as the outer failure's cause
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getCause())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("nested");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void eventually_shouldThrowNullPointerExceptionWhenTimeoutIsNull()
    {
        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.eventually(null, () -> { }))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void eventually_shouldThrowNullPointerExceptionWhenAssertionIsNull()
    {
        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.eventually(Duration.ofSeconds(5), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void eventually_shouldThrowIllegalArgumentExceptionWhenTimeoutIsZero()
    {
        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.eventually(Duration.ZERO, () -> { }))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventually_shouldThrowIllegalArgumentExceptionWhenTimeoutIsNegative()
    {
        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.eventually(Duration.ofSeconds(-1), () -> { }))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
