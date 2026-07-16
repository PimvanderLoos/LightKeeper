package nl.pim16aap2.lightkeeper.protocol;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabCompletePlayerTest
{
    @Test
    void command_shouldRejectBlankRequestId()
    {
        // setup
        final UUID uuid = UUID.randomUUID();

        // execute + verify
        assertThatThrownBy(() -> new TabCompletePlayer.Command("   ", uuid, "gamemode"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requestId");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void command_shouldRejectNullUuid()
    {
        // execute + verify
        assertThatThrownBy(() -> new TabCompletePlayer.Command("request-1", null, "gamemode"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("uuid");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void command_shouldRejectNullCommandLine()
    {
        // execute + verify
        assertThatThrownBy(() -> new TabCompletePlayer.Command("request-1", UUID.randomUUID(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("commandLine");
    }

    @Test
    void command_shouldPreserveTrailingWhitespaceWithoutStripping()
    {
        // setup
        final UUID uuid = UUID.randomUUID();

        // execute — a trailing space selects argument completion and is semantically load-bearing
        final TabCompletePlayer.Command command = new TabCompletePlayer.Command("request-1", uuid, "/gamemode ");

        // verify
        assertThat(command.commandLine()).isEqualTo("/gamemode ");
    }

    @Test
    void command_shouldAllowBlankCommandLine()
    {
        // setup
        final UUID uuid = UUID.randomUUID();

        // execute — unlike ExecutePlayerCommand, a whitespace-only buffer is a valid tab-complete input
        final TabCompletePlayer.Command command = new TabCompletePlayer.Command("request-1", uuid, "   ");

        // verify
        assertThat(command.commandLine()).isEqualTo("   ");
    }

    @Test
    void response_shouldDefensivelyCopyCompletionsSoLaterMutationsDoNotLeak()
    {
        // setup
        final List<String> source = new ArrayList<>(List.of("help"));
        final TabCompletePlayer.Response response = new TabCompletePlayer.Response(source);

        // execute
        source.add("plugins");

        // verify
        assertThat(response.completions()).containsExactly("help");
        assertThatThrownBy(() -> response.completions().add("me"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify default-on-null.
    void response_shouldDefaultNullCompletionsToEmptyList()
    {
        // setup + execute
        final TabCompletePlayer.Response response = new TabCompletePlayer.Response(null);

        // verify
        assertThat(response.completions()).isEmpty();
    }

    @Test
    void command_shouldRoundTripThroughJson()
        throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
        final TabCompletePlayer.Command original = new TabCompletePlayer.Command("req-tc", uuid, "/lktestg");

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(TabCompletePlayer.Command.class);
        final TabCompletePlayer.Command result = (TabCompletePlayer.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-tc");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.commandLine()).isEqualTo("/lktestg");
    }

    @Test
    void response_shouldRoundTripThroughJson()
        throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final TabCompletePlayer.Response original = new TabCompletePlayer.Response(List.of("lktestgui", "lktestlocale"));

        // execute
        final String json = mapper.writeValueAsString(original);
        final TabCompletePlayer.Response result = mapper.readValue(json, TabCompletePlayer.Response.class);

        // verify
        assertThat(result.completions()).containsExactly("lktestgui", "lktestlocale");
    }
}
