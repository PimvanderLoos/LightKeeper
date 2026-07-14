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
    void stackTrace_shouldNotExposeServerErrorEntryState()
    {
        // setup
        final List<String> source = new ArrayList<>(List.of("java.lang.IllegalStateException: boom"));
        final ServerErrorEntry entry = new ServerErrorEntry(
            0L, "ERROR", "ERROR", "logger", "thread", "message", null, null, source);

        // execute
        source.add("\tat net.example.SomePlugin.onEvent(SomePlugin.java:1)");

        // verify
        assertThat(entry.stackTrace()).containsExactly("java.lang.IllegalStateException: boom");
        assertThatThrownBy(() -> entry.stackTrace().add("extra"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify default-on-null.
    void stackTrace_shouldDefaultToEmptyListWhenServerErrorEntryConstructedWithNull()
    {
        // setup + execute
        final ServerErrorEntry entry = new ServerErrorEntry(
            0L, "ERROR", "ERROR", "logger", "thread", "message", null, null, null);

        // verify
        assertThat(entry.stackTrace()).isEmpty();
    }

    @Test
    void errors_shouldNotExposeGetServerErrorsResponseState()
    {
        // setup
        final ServerErrorEntry entry = new ServerErrorEntry(
            0L, "ERROR", "ERROR", "logger", "thread", "message", null, null, List.of());
        final List<ServerErrorEntry> source = new ArrayList<>(List.of(entry));
        final GetServerErrors.Response response = new GetServerErrors.Response(source, 0L, true);

        // execute
        source.clear();

        // verify
        assertThat(response.errors()).containsExactly(entry);
        assertThatThrownBy(() -> response.errors().add(entry))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify default-on-null.
    void errors_shouldDefaultToEmptyListWhenGetServerErrorsResponseConstructedWithNull()
    {
        // setup + execute
        final GetServerErrors.Response response = new GetServerErrors.Response(null, 0L, true);

        // verify
        assertThat(response.errors()).isEmpty();
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
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
