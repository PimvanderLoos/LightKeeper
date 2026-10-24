package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import nl.pim16aap2.lightkeeper.framework.ServerErrorsHandle;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Non-consuming assertions over live structured error and warning observations. */
public final class ServerErrorsAssert extends AbstractAssert<ServerErrorsAssert, @Nullable ServerErrorsHandle>
{
    ServerErrorsAssert(@Nullable ServerErrorsHandle actual)
    {
        super(actual, ServerErrorsAssert.class);
    }

    /** @return Frozen assertions over currently captured entries; recreate navigation inside retries. */
    public ListAssert<ServerErrorSnapshot> captured()
    {
        return extracting(ServerErrorsHandle::getCaptured, Assertions::assertThat);
    }

    /** @return This assertion after checking for errors; warnings are ignored. */
    public ServerErrorsAssert hasNoErrors()
    {
        return hasNoErrors(error -> false);
    }

    /**
     * Checks for error-severity entries outside an explicit allowlist.
     *
     * @param allowedErrors
     *     Predicate matching known-benign errors.
     * @return This assertion.
     */
    public ServerErrorsAssert hasNoErrors(Predicate<ServerErrorSnapshot> allowedErrors)
    {
        Objects.requireNonNull(allowedErrors, "allowedErrors may not be null.");
        return hasNoSeverity(ServerErrorSnapshot.Severity.ERROR, "errors", allowedErrors);
    }

    /** @return This assertion after checking for warning-severity entries; errors are checked separately. */
    public ServerErrorsAssert hasNoWarnings()
    {
        return hasNoSeverity(ServerErrorSnapshot.Severity.WARNING, "warnings", error -> false);
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the live handle.
    private ServerErrorsAssert hasNoSeverity(
        ServerErrorSnapshot.Severity severity, String label, Predicate<ServerErrorSnapshot> allowed)
    {
        isNotNull();
        final List<ServerErrorSnapshot> failing = actual.getCaptured().stream()
            .filter(entry -> entry.severity() == severity)
            .filter(entry -> !allowed.test(entry))
            .toList();
        if (!failing.isEmpty())
            failWithMessage(
                "Expected no captured server %s, but found %d:%n%s",
                label,
                failing.size(),
                render(failing)
            );
        return this;
    }

    private static String render(List<ServerErrorSnapshot> errors)
    {
        final StringBuilder rendered = new StringBuilder(256);
        for (final var error : errors)
            rendered.append(error.toDisplayString(15));
        return rendered.toString();
    }
}
