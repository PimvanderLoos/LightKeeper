package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChatComponentSnapshotTest
{
    @Test
    void clickRunCommand_shouldExtractCommandFromRootClickEvent()
    {
        // setup — the 1.21.5+ wire spells the field click_event (snake_case)
        final ChatComponentSnapshot component = new ChatComponentSnapshot(
            "{\"text\":\"click me\",\"click_event\":{\"action\":\"run_command\",\"command\":\"/aa confirm\"}}");

        // execute
        final Optional<String> command = component.clickRunCommand();

        // verify
        assertThat(command).contains("/aa confirm");
    }

    @Test
    void clickRunCommand_shouldMatchClickEventNestedInExtraChild()
    {
        // setup — NMS serializes clickable text inside an 'extra' child rather than the root object
        final ChatComponentSnapshot component = new ChatComponentSnapshot(
            "{\"text\":\"\",\"extra\":[{\"text\":\"click me\","
                + "\"click_event\":{\"action\":\"run_command\",\"command\":\"/lktestgui\"}}]}");

        // execute
        final Optional<String> command = component.clickRunCommand();

        // verify — a root-only lookup would miss this
        assertThat(command).contains("/lktestgui");
    }

    @Test
    void clickRunCommand_shouldReturnEmptyWhenComponentHasNoClickEvent()
    {
        // setup
        final ChatComponentSnapshot component = new ChatComponentSnapshot("{\"text\":\"plain\"}");

        // execute + verify
        assertThat(component.clickRunCommand()).isEmpty();
    }

    @Test
    void clickRunCommand_shouldReturnEmptyForSuggestCommandOnlyComponent()
    {
        // setup — per-action matching: a suggest_command click must not satisfy clickRunCommand()
        final ChatComponentSnapshot component = new ChatComponentSnapshot(
            "{\"text\":\"type\",\"click_event\":{\"action\":\"suggest_command\",\"command\":\"/aa create \"}}");

        // execute + verify
        assertThat(component.clickRunCommand()).isEmpty();
    }

    @Test
    void clickRunCommand_shouldReturnEmptyForLegacyCamelCaseClickEventKey()
    {
        // setup — extraction keys on the modern click_event only; the pre-1.21.5 clickEvent is not the wire
        // format on supported versions, so it is not mined for a command
        final ChatComponentSnapshot component = new ChatComponentSnapshot(
            "{\"text\":\"click me\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/aa confirm\"}}");

        // execute + verify
        assertThat(component.clickRunCommand()).isEmpty();
    }

    @Test
    void clickRunCommand_shouldReturnEmptyForMalformedJson()
    {
        // setup — malformed component JSON degrades to empty rather than throwing
        final ChatComponentSnapshot component = new ChatComponentSnapshot("{\"text\":\"broken\" BROKEN");

        // execute + verify
        assertThat(component.clickRunCommand()).isEmpty();
    }

    @Test
    void clickSuggestedCommand_shouldExtractCommandAndPreserveTrailingSpace()
    {
        // setup — suggest-command payloads often end with a space to prime the next argument
        final ChatComponentSnapshot component = new ChatComponentSnapshot(
            "{\"text\":\"type\",\"click_event\":{\"action\":\"suggest_command\",\"command\":\"/aa create \"}}");

        // execute
        final Optional<String> command = component.clickSuggestedCommand();

        // verify — the trailing space must survive intact
        assertThat(command).contains("/aa create ");
    }

    @Test
    void clickRunCommand_shouldReturnItsOwnActionWhenRunAndSuggestCoexist()
    {
        // setup — a paginated control can carry both a run_command and a suggest_command run
        final ChatComponentSnapshot component = new ChatComponentSnapshot(
            "{\"text\":\"\",\"extra\":["
                + "{\"text\":\"[<]\",\"click_event\":{\"action\":\"suggest_command\",\"command\":\"/page \"}},"
                + "{\"text\":\"[>]\",\"click_event\":{\"action\":\"run_command\",\"command\":\"/page 2\"}}]}");

        // execute + verify — each accessor returns its own action's payload, never the other's
        assertThat(component.clickRunCommand()).contains("/page 2");
        assertThat(component.clickSuggestedCommand()).contains("/page ");
    }
}
