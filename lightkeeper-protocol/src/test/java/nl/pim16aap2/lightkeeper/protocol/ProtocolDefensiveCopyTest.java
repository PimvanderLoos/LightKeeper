package nl.pim16aap2.lightkeeper.protocol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProtocolDefensiveCopyTest
{
    @Test
    void slots_shouldNotExposeCommandState()
    {
        // setup
        final int[] source = {1, 2};
        final DragMenuSlots.Command command =
            new DragMenuSlots.Command("request-1", UUID.randomUUID(), "minecraft:stone", source);

        // execute
        source[0] = 9;
        final int[] returned = command.slots();
        returned[1] = 8;

        // verify
        assertThat(command.slots()).containsExactly(1, 2);
    }

    @Test
    void messages_shouldNotExposeResponseState()
    {
        // setup
        final List<String> source = new ArrayList<>(List.of("first"));
        final GetPlayerMessages.Response response = new GetPlayerMessages.Response(source);

        // execute
        source.add("second");

        // verify
        assertThat(response.messages()).containsExactly("first");
        assertThatThrownBy(() -> response.messages().add("third"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void response_shouldRejectNullMessages()
    {
        // setup
        final List<String> messages = null;

        // execute
        final Throwable throwable = catchThrowable(() -> new GetPlayerMessages.Response(messages));

        // verify
        assertThat(throwable)
            .isInstanceOf(NullPointerException.class)
            .hasMessage("messages");
    }
}
