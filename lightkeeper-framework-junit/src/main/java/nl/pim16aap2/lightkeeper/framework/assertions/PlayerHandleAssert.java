package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Assertions for synthetic player state.
 */
public final class PlayerHandleAssert extends AbstractAssert<PlayerHandleAssert, @Nullable PlayerHandle>
{
    PlayerHandleAssert(@Nullable PlayerHandle actual)
    {
        super(actual, PlayerHandleAssert.class);
    }

    /**
     * Asserts that the player has the expected name.
     *
     * @param expectedName
     *     Expected player name.
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert hasName(String expectedName)
    {
        final var actual = nonNullActual();
        if (!Objects.equals(actual.name(), expectedName))
            failWithMessage("Expected player name '%s' but was '%s'.", expectedName, actual.name());
        return this;
    }

    /**
     * Asserts that at least one received message contains the requested text fragment.
     *
     * @param expectedFragment
     *     Required message fragment.
     * @return This assertion for fluent chaining.
     */
    public PlayerHandleAssert receivedMessage(String expectedFragment)
    {
        final var actual = nonNullActual();
        Assertions
            .assertThat(actual.receivedMessagesText())
            .contains(expectedFragment);
        return this;
    }

    /**
     * Returns an AssertJ string assertion over all received messages concatenated into one text block.
     *
     * @return AssertJ string assertion.
     */
    public AbstractStringAssert<?> receivedMessagesText()
    {
        final var actual = nonNullActual();
        return Assertions.assertThat(actual.receivedMessagesText());
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // we call isNotNull() first, so actual is not null after that
    private PlayerHandle nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
