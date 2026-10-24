package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.CommandResult;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

/** Assertions over a returned command dispatch result, without executing or retrying the command. */
public final class CommandResultAssert extends AbstractAssert<CommandResultAssert, @Nullable CommandResult>
{
    CommandResultAssert(@Nullable CommandResult actual)
    {
        super(actual, CommandResultAssert.class);
    }

    /** @return This assertion after checking that command dispatch reported success. */
    public CommandResultAssert isSuccessful()
    {
        return hasSuccessState(true);
    }

    /** @return This assertion after checking that command dispatch reported failure. */
    public CommandResultAssert isFailure()
    {
        return hasSuccessState(false);
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the result.
    private CommandResultAssert hasSuccessState(boolean expected)
    {
        isNotNull();
        if (actual.success() != expected)
            failWithMessage("Expected command dispatch success to be %s, but was %s: %s",
                expected, actual.success(), actual.message());
        return this;
    }
}
