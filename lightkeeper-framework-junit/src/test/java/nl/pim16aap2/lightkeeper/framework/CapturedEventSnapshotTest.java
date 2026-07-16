package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapturedEventSnapshotTest
{
    @Test
    void value_shouldReturnStoredValueForKnownAccessor()
    {
        // setup
        final CapturedEventSnapshot snapshot = new CapturedEventSnapshot(
            "org.bukkit.event.player.PlayerJoinEvent",
            1L,
            Map.of("isCancelled", new IProtocolValue.PBool(true)));

        // execute
        final IProtocolValue result = snapshot.value("isCancelled");

        // verify
        assertThat(result).isEqualTo(new IProtocolValue.PBool(true));
    }

    @Test
    void value_shouldReturnNullForUnknownAccessor()
    {
        // setup
        final CapturedEventSnapshot snapshot = new CapturedEventSnapshot(
            "org.bukkit.event.player.PlayerJoinEvent",
            1L,
            Map.of("isCancelled", new IProtocolValue.PBool(true)));

        // execute
        final IProtocolValue result = snapshot.value("getMissing");

        // verify
        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify default-on-null.
    void constructor_shouldDefaultToEmptyMapWhenValuesIsNull()
    {
        // setup + execute
        final CapturedEventSnapshot snapshot = new CapturedEventSnapshot("event.Class", 1L, null);

        // verify
        assertThat(snapshot.values()).isEmpty();
    }
}
