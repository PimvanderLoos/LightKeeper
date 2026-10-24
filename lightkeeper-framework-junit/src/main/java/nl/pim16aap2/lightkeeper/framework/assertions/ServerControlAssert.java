package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.IServerControl;
import nl.pim16aap2.lightkeeper.framework.Platform;
import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;

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

    /** @return Live structured error/warning assertions, without consuming or clearing entries. */
    public ServerErrorsAssert errors()
    {
        return extracting(IServerControl::errors, ServerErrorsAssert::new);
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
        return extracting(IServerControl::output, Assertions::assertThat);
    }

    /**
     * Asserts that no error-severity server entries have been captured. Warnings are ignored.
     *
     * @return This assertion for fluent chaining.
     */
    public ServerControlAssert hasNoErrors()
    {
        errors().hasNoErrors();
        return this;
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
        errors().hasNoErrors(allowedErrors);
        return this;
    }

    private ServerControlAssert hasPlatform(Platform expected)
    {
        final Platform observed = nonNullActual().platform();
        if (observed != expected)
            failWithMessage("Expected server platform to be %s but was %s.", expected, observed);
        return this;
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the non-null subject.
    private IServerControl nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
