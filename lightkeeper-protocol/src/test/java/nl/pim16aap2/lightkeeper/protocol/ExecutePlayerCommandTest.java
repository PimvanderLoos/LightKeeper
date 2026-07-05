package nl.pim16aap2.lightkeeper.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutePlayerCommandTest
{
    @Test
    void command_shouldThrowExceptionWhenCommandIsBlank()
    {
        // setup
        final UUID uuid = UUID.randomUUID();

        // execute + verify
        assertThatThrownBy(() -> new ExecutePlayerCommand.Command("request-1", uuid, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("command");
    }

    @Test
    void command_shouldStripSurroundingWhitespace()
    {
        // setup
        final UUID uuid = UUID.randomUUID();

        // execute
        final ExecutePlayerCommand.Command command =
            new ExecutePlayerCommand.Command("request-1", uuid, "  say hi  ");

        // verify
        assertThat(command.command()).isEqualTo("say hi");
    }
}
