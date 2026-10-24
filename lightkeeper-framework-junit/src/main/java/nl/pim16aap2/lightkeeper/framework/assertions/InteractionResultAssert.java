package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.InteractionResult;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

/** Assertions over a completed interaction; cancellation checks require an actual fired event. */
public final class InteractionResultAssert extends AbstractAssert<InteractionResultAssert, @Nullable InteractionResult>
{
    InteractionResultAssert(@Nullable InteractionResult actual)
    {
        super(actual, InteractionResultAssert.class);
    }

    /** @return This assertion after checking that the interaction fired a real event. */
    public InteractionResultAssert hasFiredEvent()
    {
        if (!nonNullActual().eventFired())
            failWithMessage("Expected interaction to fire an event, but no event was fired.");
        return this;
    }

    /** @return This assertion after checking that a fired event was cancelled. */
    public InteractionResultAssert isCancelled()
    {
        return hasCancellationState(true);
    }

    /** @return This assertion after checking that a fired event was not cancelled. */
    public InteractionResultAssert isNotCancelled()
    {
        return hasCancellationState(false);
    }

    private InteractionResultAssert hasCancellationState(boolean expected)
    {
        hasFiredEvent();
        if (nonNullActual().cancelled() != expected)
            failWithMessage("Expected fired interaction cancellation to be %s, but was %s.",
                expected, nonNullActual().cancelled());
        return this;
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the result.
    private InteractionResult nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
