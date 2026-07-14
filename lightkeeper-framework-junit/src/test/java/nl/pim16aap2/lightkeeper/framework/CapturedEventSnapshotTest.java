package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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
            Map.of("isCancelled", new IProtocolValue.PBool(true)));

        // execute
        final IProtocolValue result = snapshot.value("getMissing");

        // verify
        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("removal") // Intentionally exercises the deprecated-for-removal data() view.
    void data_shouldRenderEachValueAsDisplayStringInCaptureOrder()
    {
        // setup
        final Map<String, IProtocolValue> values = new LinkedHashMap<>();
        values.put("getName", new IProtocolValue.PString("Steve"));
        values.put("isCancelled", new IProtocolValue.PBool(false));
        values.put("getCount", new IProtocolValue.PNumber(3));
        final CapturedEventSnapshot snapshot = new CapturedEventSnapshot("event.Class", values);

        // execute
        final Map<String, String> rendered = snapshot.data();

        // verify
        assertThat(rendered).containsExactly(
            Map.entry("getName", "Steve"),
            Map.entry("isCancelled", "false"),
            Map.entry("getCount", "3"));
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify default-on-null.
    void constructor_shouldDefaultToEmptyMapWhenValuesIsNull()
    {
        // setup + execute
        final CapturedEventSnapshot snapshot = new CapturedEventSnapshot("event.Class", null);

        // verify
        assertThat(snapshot.values()).isEmpty();
    }
}
