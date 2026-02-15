package nl.pim16aap2.lightkeeper.agent.spigot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AgentRequestParsersTest
{
    @Test
    void parseInt_shouldParseTrimmedValue()
    {
        // setup
        final String input = " 42 ";

        // execute
        final int value = AgentRequestParsers.parseInt(input);

        // verify
        assertThat(value).isEqualTo(42);
    }

    @Test
    void parseLong_shouldParseTrimmedValue()
    {
        // setup
        final String input = " 922337203685 ";

        // execute
        final long value = AgentRequestParsers.parseLong(input);

        // verify
        assertThat(value).isEqualTo(922337203685L);
    }

    @Test
    void parseOptionalDouble_shouldReturnNullWhenInputIsNull()
    {
        // setup
        final String input = null;

        // execute
        final Double value = AgentRequestParsers.parseOptionalDouble(input);

        // verify
        assertThat(value).isNull();
    }

    @Test
    void parseOptionalDouble_shouldReturnNullWhenInputIsBlank()
    {
        // setup
        final String input = "   ";

        // execute
        final Double value = AgentRequestParsers.parseOptionalDouble(input);

        // verify
        assertThat(value).isNull();
    }

    @Test
    void parseOptionalDouble_shouldParseTrimmedValue()
    {
        // setup
        final String input = "  13.37  ";

        // execute
        final Double value = AgentRequestParsers.parseOptionalDouble(input);

        // verify
        assertThat(value).isEqualTo(13.37D);
    }

    @Test
    void parseOptionalDouble_shouldThrowExceptionWhenInputIsNotNumeric()
    {
        // setup
        final String input = "not-a-number";

        // execute + verify
        assertThatThrownBy(() -> AgentRequestParsers.parseOptionalDouble(input))
            .isInstanceOf(NumberFormatException.class);
    }
}
