package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.EventCaptureHandle;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;

/** Eager, non-consuming assertions over a live event capture buffer. */
public final class EventCaptureAssert extends AbstractAssert<EventCaptureAssert, @Nullable EventCaptureHandle>
{
    EventCaptureAssert(@Nullable EventCaptureHandle actual)
    {
        super(actual, EventCaptureAssert.class);
    }

    /**
     * Checks the number of events since registration or the last explicit clear.
     *
     * @param expected Nonnegative captured-event count.
     * @return This assertion.
     */
    public EventCaptureAssert hasEventCount(int expected)
    {
        if (expected < 0)
            throw new IllegalArgumentException("expected must be >= 0.");
        final var capture = nonNullActual();
        final int observed = capture.getCapturedEvents().size();
        if (observed != expected)
            failWithMessage("Expected capture of '%s' to contain %d events, but contained %d.",
                capture.eventClassName(), expected, observed);
        return this;
    }

    /** @return This assertion after checking that no events have been captured so far. */
    public EventCaptureAssert hasNoEvents()
    {
        return hasEventCount(0);
    }

    /** @return A frozen list of captured events; recreate navigation inside retries. */
    public ListAssert<CapturedEventSnapshot> events()
    {
        return extracting(EventCaptureHandle::getCapturedEvents, Assertions::assertThat);
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the subject.
    private EventCaptureHandle nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
