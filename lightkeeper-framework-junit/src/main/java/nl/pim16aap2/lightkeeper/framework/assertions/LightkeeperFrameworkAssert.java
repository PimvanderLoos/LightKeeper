package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.IServerControl;
import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;

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
     * Navigates to eager assertions over the framework's live server.
     *
     * @return Server assertions, including named plugin assertions.
     */
    public ServerControlAssert server()
    {
        return extracting(ILightkeeperFramework::server, ServerControlAssert::new);
    }

    /**
     * Returns an AssertJ list assertion over captured server output.
     *
     * @return AssertJ list assertion for captured server output lines.
     */
    public ListAssert<String> serverOutput()
    {
        return server().output();
    }

    /**
     * Asserts that the server is running on Paper.
     *
     * @return This assertion for fluent chaining.
     */
    public LightkeeperFrameworkAssert isPaper()
    {
        server().isPaper();
        return this;
    }

    /**
     * Asserts that the server is running on Spigot.
     *
     * @return This assertion for fluent chaining.
     */
    public LightkeeperFrameworkAssert isSpigot()
    {
        server().isSpigot();
        return this;
    }

    /**
     * Asserts that no error-severity server errors were captured.
     *
     * <p>Backed by structured capture (see {@link IServerControl#errors()}): entries are matched
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
        server().hasNoErrors(allowedErrors);
        return this;
    }
}
