package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * AssertJ entrypoints for LightKeeper handles.
 */
public final class LightkeeperAssertions extends Assertions
{
    /**
     * Poll interval between retry attempts; matches the framework's {@code waitUntil} polling cadence.
     */
    private static final long RETRY_POLL_INTERVAL_MILLIS = 50L;

    private static final ThreadLocal<Boolean> INSIDE_EVENTUALLY = new ThreadLocal<>();

    private LightkeeperAssertions()
    {
    }

    /**
     * Creates an assertion chain for world-related state.
     *
     * @param actual
     *     World handle under test.
     * @return World assertion entrypoint.
     */
    public static WorldHandleAssert assertThat(@Nullable WorldHandle actual)
    {
        return new WorldHandleAssert(actual);
    }

    /**
     * Creates an assertion chain for menu-related state.
     *
     * @param actual
     *     Menu handle under test.
     * @return Menu assertion entrypoint.
     */
    public static MenuHandleAssert assertThat(@Nullable MenuHandle actual)
    {
        return new MenuHandleAssert(actual);
    }

    /**
     * Creates an assertion chain for player-related state.
     *
     * @param actual
     *     Player handle under test.
     * @return Player assertion entrypoint.
     */
    public static PlayerHandleAssert assertThat(@Nullable PlayerHandle actual)
    {
        return new PlayerHandleAssert(actual);
    }

    /**
     * Creates an assertion chain for framework-level runtime state.
     *
     * @param actual
     *     Framework under test.
     * @return Framework assertion entrypoint.
     */
    public static LightkeeperFrameworkAssert assertThat(@Nullable ILightkeeperFramework actual)
    {
        return new LightkeeperFrameworkAssert(actual);
    }

    /**
     * Retries an assertion until it passes or the timeout expires.
     *
     * <p>Eager assertions never poll; this is the visible opt-in retry over the exact same assertion. The
     * assertion is re-run in full — probe and matcher — every {@code 50 ms}, so it only makes sense over LIVE
     * subjects that re-query the server per call (block types, inventories, menus, messages, server errors);
     * retrying a frozen snapshot can never change the outcome and simply burns the timeout.
     *
     * <p>On timeout, the thrown {@link AssertionError} carries the retry history (attempt count and elapsed
     * time) and the last attempt's failure as its cause — strictly more information than a generic
     * "condition not met". A non-assertion exception (e.g. a closed framework) fails fast on the first
     * occurrence instead of burning the timeout; nesting {@code eventually} inside {@code eventually} is
     * detected and rejected.
     *
     * @param timeout
     *     Maximum time to keep retrying; must be positive.
     * @param assertion
     *     The assertion to retry, typically a lambda re-running an eager {@code assertThat(...)} chain.
     */
    public static void eventually(Duration timeout, RetryingAssertion assertion)
    {
        Objects.requireNonNull(timeout, "timeout may not be null.");
        Objects.requireNonNull(assertion, "assertion may not be null.");
        if (timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("timeout must be positive.");
        if (Boolean.TRUE.equals(INSIDE_EVENTUALLY.get()))
            throw new NestedEventuallyException();

        INSIDE_EVENTUALLY.set(Boolean.TRUE);
        try
        {
            runWithRetries(timeout, assertion);
        }
        finally
        {
            INSIDE_EVENTUALLY.remove();
        }
    }

    private static void runWithRetries(Duration timeout, RetryingAssertion assertion)
    {
        final long startNanos = System.nanoTime();
        final long deadlineNanos = startNanos + timeout.toNanos();
        int attempts = 0;
        while (true)
        {
            attempts++;
            final AssertionError lastFailure;
            try
            {
                assertion.run();
                return;
            }
            catch (AssertionError assertionError)
            {
                lastFailure = assertionError;
            }
            catch (NestedEventuallyException nestedEventuallyException)
            {
                // A programmer error, not an evaluation failure: surface the nesting message directly.
                throw nestedEventuallyException;
            }
            catch (Exception exception)
            {
                // Non-assertion failures (closed framework, protocol errors) will not heal by retrying.
                throw new IllegalStateException(
                    "Assertion evaluation failed with a non-assertion error on attempt %d.".formatted(attempts),
                    exception);
            }

            if (System.nanoTime() >= deadlineNanos)
            {
                final double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                throw new AssertionError(
                    "Assertion did not pass within %s (%d attempts over %.1fs). Last failure: %s".formatted(
                        timeout, attempts, elapsedSeconds, lastFailure.getMessage()),
                    lastFailure);
            }

            try
            {
                Thread.sleep(RETRY_POLL_INTERVAL_MILLIS);
            }
            catch (InterruptedException interruptedException)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while retrying an assertion.", interruptedException);
            }
        }
    }

    /**
     * An assertion that {@link #eventually(Duration, RetryingAssertion)} re-runs until it passes.
     */
    @FunctionalInterface
    public interface RetryingAssertion
    {
        /**
         * Runs the assertion once.
         *
         * @throws Exception
         *     Assertion failures surface as {@link AssertionError} and are retried; any other failure is
         *     wrapped in an {@link IllegalStateException} (with the original as its cause) and fails fast.
         */
        void run()
            throws Exception;
    }

    /**
     * Thrown when {@code eventually} is nested inside another {@code eventually}; a distinct type so the
     * outer retry loop can surface the nesting message directly instead of wrapping it.
     */
    private static final class NestedEventuallyException extends IllegalStateException
    {
        NestedEventuallyException()
        {
            super("eventually(...) may not be nested inside another eventually(...); retry the whole "
                + "assertion in a single call instead.");
        }
    }
}
