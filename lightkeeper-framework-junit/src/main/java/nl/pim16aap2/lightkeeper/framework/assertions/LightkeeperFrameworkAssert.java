package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.Platform;
import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Assertions for framework-level runtime state.
 */
public final class LightkeeperFrameworkAssert
    extends AbstractAssert<LightkeeperFrameworkAssert, @Nullable ILightkeeperFramework>
{
    LightkeeperFrameworkAssert(@Nullable ILightkeeperFramework actual)
    {
        super(actual, LightkeeperFrameworkAssert.class);
    }

    /**
     * Returns an AssertJ list assertion over captured server output.
     *
     * @return AssertJ list assertion for captured server output lines.
     */
    public ListAssert<String> serverOutput()
    {
        return Assertions.assertThat(nonNullActual().serverOutput());
    }

    /**
     * Asserts that the server is running on Paper.
     *
     * @return This assertion for fluent chaining.
     */
    public LightkeeperFrameworkAssert isPaper()
    {
        final var actual = nonNullActual();
        if (actual.platform() != Platform.PAPER)
            failWithMessage("Expected server platform to be PAPER but was %s.", actual.platform());
        return this;
    }

    /**
     * Asserts that the server is running on Spigot.
     *
     * @return This assertion for fluent chaining.
     */
    public LightkeeperFrameworkAssert isSpigot()
    {
        final var actual = nonNullActual();
        if (actual.platform() != Platform.SPIGOT)
            failWithMessage("Expected server platform to be SPIGOT but was %s.", actual.platform());
        return this;
    }

    /**
     * Asserts that no error-severity server errors were captured.
     *
     * <p>Backed by structured capture (see {@link ILightkeeperFramework#serverErrors()}): entries are matched
     * on their captured severity, not on console-line substrings, so a log message merely containing the word
     * "exception" no longer trips this assertion. Warnings are never counted.
     *
     * @return This assertion for fluent chaining.
     */
    public LightkeeperFrameworkAssert hasNoServerErrors()
    {
        return hasNoServerErrors(error -> false);
    }

    /**
     * Asserts that no error-severity server errors were captured, ignoring entries matched by the allowlist.
     *
     * <p>Use the allowlist for known-benign errors, matching on structured fields rather than console text:
     * <pre>{@code
     * assertThat(framework).hasNoServerErrors(error ->
     *     error.loggerName().equals("net.minecraft.server")
     *         && error.message().contains("moving_piston"));
     * }</pre>
     *
     * @param allowedErrors
     *     Predicate matching captured errors that should not fail this assertion.
     * @return This assertion for fluent chaining.
     */
    public LightkeeperFrameworkAssert hasNoServerErrors(Predicate<ServerErrorSnapshot> allowedErrors)
    {
        Objects.requireNonNull(allowedErrors, "allowedErrors may not be null.");
        final List<ServerErrorSnapshot> failingErrors = nonNullActual().serverErrors().getCaptured().stream()
            .filter(error -> error.severity() == ServerErrorSnapshot.Severity.ERROR)
            .filter(error -> !allowedErrors.test(error))
            .toList();
        if (!failingErrors.isEmpty())
        {
            failWithMessage(
                "Expected no captured server errors, but found %d:%n%s",
                failingErrors.size(),
                renderServerErrors(failingErrors)
            );
        }
        return this;
    }

    /**
     * Renders captured errors for assertion failure messages: one header line per error plus an indented,
     * capped stack trace so the failure carries its own diagnostic context.
     */
    private static String renderServerErrors(List<ServerErrorSnapshot> errors)
    {
        final int maxRenderedStackTraceLines = 15;
        final StringBuilder rendered = new StringBuilder(256);
        for (final ServerErrorSnapshot error : errors)
        {
            rendered
                .append("[%s] %s (thread: %s): %s".formatted(
                    error.levelName(),
                    error.loggerName(),
                    error.threadName().isEmpty() ? "?" : error.threadName(),
                    error.message()))
                .append(System.lineSeparator());

            final List<String> stackTrace = error.stackTrace();
            stackTrace.stream()
                .limit(maxRenderedStackTraceLines)
                .forEach(line -> rendered.append("    ").append(line).append(System.lineSeparator()));
            if (stackTrace.size() > maxRenderedStackTraceLines)
                rendered
                    .append("    ... (%d more stack trace lines)"
                        .formatted(stackTrace.size() - maxRenderedStackTraceLines))
                    .append(System.lineSeparator());
        }
        return rendered.toString();
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // we call isNotNull() first, so actual is not null after that
    private ILightkeeperFramework nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
