package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.*;

/** Tests literal-text/action association, inheritance, boundaries and malformed observations. */
class ChatComponentAssertTest
{
    @Test
    void runsCommand_shouldMatchExactPayloadAndLiteralText()
    {
        // setup
        final var component = new ChatComponentSnapshot(
            """
                {"text":"Open","click_event":{"action":"run_command","command":"/open"}}
                """
        );
        final var assertion = assertThat(component);

        // execute
        final var result = assertion
            .hasClickableText("Open")
            .runsCommand("Open", "/open");

        // verify
        assertThat(result).isSameAs(assertion);
        assertThat(component.clickActions()).hasSize(1);
    }

    @Test
    void hasClickableText_shouldRejectTextInUnrelatedSibling()
    {
        // setup
        final var component = new ChatComponentSnapshot(
            """
                {"text":"","extra":[{"text":"Open"},{"text":"Close",
                  "click_event":{"action":"run_command","command":"/close"}}]}
                """
        );

        // execute
        final Throwable failure = catchThrowable(
            () -> assertThat(component).hasClickableText("Open")
        );

        // verify
        assertThat(failure)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Open");

        assertThat(component).runsCommand("Close", "/close");
    }

    @Test
    void hasClickableText_shouldRejectTextFoundOnlyInCommandPayload()
    {
        // setup
        final var component = new ChatComponentSnapshot(
            """
                {"text":"Click","click_event":{"action":"run_command","command":"/Open"}}
                """
        );

        // execute
        final Throwable failure = catchThrowable(
            () -> assertThat(component).hasClickableText("Open")
        );

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
    }

    @Test
    void runsCommand_shouldInheritAndMergeAdjacentLiteralRuns()
    {
        // setup
        final var component = new ChatComponentSnapshot(
            """
                {"text":"","click_event":{"action":"run_command","command":"/open"},
                  "extra":[{"text":"Click "},{"text":"me"}]}
                """
        );

        // execute
        assertThat(component).runsCommand("Click me", "/open");

        // verify
        assertThat(component.clickActions())
            .singleElement()
            .satisfies(action -> assertThat(action.text()).isEqualTo("Click me"));
    }

    @Test
    void runsCommand_shouldInheritFirstArrayComponentStyle()
    {
        // setup
        final var component = new ChatComponentSnapshot(
            """
                [{"text":"Click ","click_event":{"action":"run_command","command":"/open"}},{"text":"me"}]
                """
        );

        // execute
        assertThat(component).runsCommand("Click me", "/open");

        // verify
        assertThat(component.clickRunCommand()).contains("/open");
    }

    @Test
    void suggestsCommand_shouldRespectChildOverrideAndTrailingSpace()
    {
        // setup
        final var component = new ChatComponentSnapshot(
            """
                {"text":"Parent","click_event":{"action":"run_command","command":"/parent"},
                  "extra":[{"text":"Child","click_event":{"action":"suggest_command","command":"/child "}}]}
                """
        );

        // execute
        assertThat(component)
            .runsCommand("Parent", "/parent")
            .suggestsCommand("Child", "/child ");

        final Throwable failure = catchThrowable(
            () -> assertThat(component).runsCommand("Child", "/parent")
        );

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
        assertThat(component.clickSuggestedCommand()).contains("/child ");
    }

    @Test
    void hasClickableText_shouldNotMergeAcrossNonClickableText()
    {
        // setup
        final var component = new ChatComponentSnapshot(
            """
                {"text":"","extra":[
                  {"text":"Click","click_event":{"action":"run_command","command":"/open"}},
                  {"text":" "},
                  {"text":"me","click_event":{"action":"run_command","command":"/open"}}]}
                """
        );

        // execute
        final Throwable failure = catchThrowable(
            () -> assertThat(component).hasClickableText("Click me")
        );

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
        assertThat(component.clickActions()).hasSize(2);
    }

    @Test
    void hasClickableText_shouldIgnoreHoverContentAndTranslationKeys()
    {
        // setup
        final var hover = new ChatComponentSnapshot(
            """
                {"text":"Main","hover_event":{"action":"show_text","value":
                  {"text":"Open","click_event":{"action":"run_command","command":"/open"}}}}
                """
        );
        final var translated = new ChatComponentSnapshot(
            """
                {"translate":"Open","click_event":{"action":"run_command","command":"/open"}}
                """
        );

        // execute
        final Throwable hoverFailure = catchThrowable(
            () -> assertThat(hover).hasClickableText("Open")
        );

        final Throwable keyFailure = catchThrowable(
            () -> assertThat(translated).hasClickableText("Open")
        );

        // verify
        assertThat(hoverFailure).isInstanceOf(AssertionError.class);
        assertThat(keyFailure).isInstanceOf(AssertionError.class);
        assertThat(hover.clickRunCommand()).isEmpty();
    }

    @Test
    void runsCommand_shouldSupportLegacyPayloadAndDecodedEscapes()
    {
        // setup
        final var component = new ChatComponentSnapshot(
            """
                {"text":"Click\\u0020me","clickEvent":{"action":"run_command","value":"/open"}}
                """
        );

        // execute
        assertThat(component).runsCommand("Click me", "/open");

        // verify
        assertThat(component.clickRunCommand()).contains("/open");
    }

    @Test
    void malformedInput_shouldFailFastRatherThanSatisfyNegativeCollectionAssertions()
    {
        // setup
        final var component = new ChatComponentSnapshot("{\"text\":\"Open\" BROKEN");

        // execute
        final Throwable failure = catchThrowable(
            () -> assertThat(List.of(component))
                .noneSatisfy(value -> assertThat(value).hasClickableText("Open"))
        );

        // verify
        assertThat(failure)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to parse")
            .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(component.clickRunCommand()).isEmpty();
    }

    @Test
    void hasClickableText_shouldRejectEmptyFragmentBeforeDecoding()
    {
        // setup
        final var assertion = assertThat(new ChatComponentSnapshot("BROKEN"));

        // execute
        final Throwable failure = catchThrowable(() -> assertion.hasClickableText(""));

        // verify
        assertThat(failure)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void hasClickableText_shouldRejectNullComponent()
    {
        // setup
        final var assertion = new ChatComponentAssert(null);

        // execute
        final Throwable failure = catchThrowable(() -> assertion.hasClickableText("Open"));

        // verify
        assertThat(failure).isInstanceOf(AssertionError.class);
    }

    @Test
    void clickActions_shouldRejectUnsupportedStructureAndKeepCommandHelpersFailSoft()
    {
        // setup
        final var component = new ChatComponentSnapshot(
            """
                {"text":"Open","extra":{}}
                """
        );

        // execute
        final Throwable failure = catchThrowable(component::clickActions);

        // verify
        assertThat(failure)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("array");

        assertThat(component.clickRunCommand()).isEmpty();
    }
}
