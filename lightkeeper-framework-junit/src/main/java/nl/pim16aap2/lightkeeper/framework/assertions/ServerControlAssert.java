package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.IServerControl;
import nl.pim16aap2.lightkeeper.framework.Platform;
import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Eager assertions over live server observations.
 *
 * <p>Each assertion reads current state. Use {@link LightkeeperAssertions#eventually} for explicit retries.
 */
public final class ServerControlAssert extends AbstractAssert<ServerControlAssert, @Nullable IServerControl>
{
    ServerControlAssert(@Nullable IServerControl actual)
    {
        super(actual, ServerControlAssert.class);
    }

    /**
     * Selects a plugin for live presence, enabled-state, and version assertions without querying the server yet.
     *
     * @param pluginName
     *     Plugin name from its {@code plugin.yml}; must not be blank.
     * @return Plugin assertions that re-query the named plugin on each evaluation.
     */
    public ServerPluginAssert plugin(String pluginName)
    {
        return extracting(Function.identity(), server -> new ServerPluginAssert(server, pluginName));
    }

    /**
     * Asserts that the server runs on Paper.
     *
     * @return This assertion for fluent chaining.
     */
    public ServerControlAssert isPaper()
    {
        return hasPlatform(Platform.PAPER);
    }

    /**
     * Asserts that the server runs on Spigot.
     *
     * @return This assertion for fluent chaining.
     */
    public ServerControlAssert isSpigot()
    {
        return hasPlatform(Platform.SPIGOT);
    }

    /**
     * Returns ordinary list assertions over the output captured at this navigation call.
     *
     * <p>Recreate this navigation inside an {@code eventually} callback to observe new output.
     *
     * @return Assertions over the captured output lines.
     */
    public ListAssert<String> output()
    {
        return Assertions.assertThat(nonNullActual().output());
    }

    /**
     * Asserts that no error-severity server entries have been captured. Warnings are ignored.
     *
     * @return This assertion for fluent chaining.
     */
    public ServerControlAssert hasNoErrors()
    {
        return hasNoErrors(error -> false);
    }

    /**
     * Asserts that no error-severity entries remain after applying an allowlist.
     *
     * @param allowedErrors
     *     Predicate matching known-benign captured errors to ignore.
     * @return This assertion for fluent chaining.
     */
    public ServerControlAssert hasNoErrors(Predicate<ServerErrorSnapshot> allowedErrors)
    {
        Objects.requireNonNull(allowedErrors, "allowedErrors may not be null.");
        final List<ServerErrorSnapshot> failingErrors = nonNullActual().errors().getCaptured().stream()
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

    private ServerControlAssert hasPlatform(Platform expected)
    {
        final Platform observed = nonNullActual().platform();
        if (observed != expected)
            failWithMessage("Expected server platform to be %s but was %s.", expected, observed);
        return this;
    }

    /**
     * Renders one header per captured error and caps each stack trace at fifteen lines.
     */
    private static String renderServerErrors(List<ServerErrorSnapshot> errors)
    {
        final int maxRenderedStackTraceLines = 15;
        final StringBuilder rendered = new StringBuilder(256);
        for (final ServerErrorSnapshot error : errors)
            rendered.append(error.toDisplayString(maxRenderedStackTraceLines));
        return rendered.toString();
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the non-null subject.
    private IServerControl nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
