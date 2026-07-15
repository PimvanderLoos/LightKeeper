package nl.pim16aap2.lightkeeper.protocol;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Jackson round-trip serialization and polymorphic deserialization for every {@link IProtocolValue} leaf,
 * nested combinations, {@link IProtocolValue.PNumber} normalization, {@link IProtocolValue.PRecord} field-order
 * preservation, and a {@link GetCapturedEvents.Response} round-trip carrying a typed event map.
 */
class ProtocolValueSerializationTest
{
    // -----------------------------------------------------------------------
    // Round-trip: every leaf
    // -----------------------------------------------------------------------

    @Test
    void serialize_pString_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final IProtocolValue.PString original = new IProtocolValue.PString("hello");

        // execute
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(json).contains("\"type\":\"STRING\"");
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void serialize_pNumber_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final IProtocolValue.PNumber original = new IProtocolValue.PNumber(42);

        // execute
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(json).contains("\"type\":\"NUMBER\"");
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void serialize_pBool_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final IProtocolValue.PBool original = new IProtocolValue.PBool(true);

        // execute
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(json).contains("\"type\":\"BOOL\"");
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void serialize_pUuid_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final IProtocolValue.PUuid original =
            new IProtocolValue.PUuid(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        // execute
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(json).contains("\"type\":\"UUID\"");
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void serialize_pEnum_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final IProtocolValue.PEnum original =
            new IProtocolValue.PEnum("org.bukkit.event.block.Action", "RIGHT_CLICK_BLOCK");

        // execute
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(json).contains("\"type\":\"ENUM\"");
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void serialize_pList_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final IProtocolValue.PList original = new IProtocolValue.PList(
            List.of(new IProtocolValue.PString("a"), new IProtocolValue.PString("b")));

        // execute
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(json).contains("\"type\":\"LIST\"");
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void serialize_pRecord_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final IProtocolValue.PRecord original =
            new IProtocolValue.PRecord(Map.of("label", new IProtocolValue.PString("value")));

        // execute
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(json).contains("\"type\":\"RECORD\"");
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void serialize_pRef_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final IProtocolValue.PRef original =
            new IProtocolValue.PRef("org.bukkit.entity.Player", "00000000-0000-0000-0000-000000000002");

        // execute
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(json).contains("\"type\":\"REF\"");
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void serialize_pDropped_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final IProtocolValue.PDropped original =
            new IProtocolValue.PDropped("getBroken", "capture-failed: IllegalStateException");

        // execute
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(json).contains("\"type\":\"DROPPED\"");
        assertThat(deserialized).isEqualTo(original);
    }

    // -----------------------------------------------------------------------
    // Nested case: PRecord containing PList containing PString
    // -----------------------------------------------------------------------

    @Test
    void serialize_pRecordContainingPListContainingPString_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final IProtocolValue.PRecord original = new IProtocolValue.PRecord(Map.of(
            "tags",
            new IProtocolValue.PList(List.of(new IProtocolValue.PString("a"), new IProtocolValue.PString("b")))
        ));

        // execute
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(deserialized).isInstanceOf(IProtocolValue.PRecord.class);
        final IProtocolValue.PRecord result = (IProtocolValue.PRecord) deserialized;
        assertThat(result.fields().get("tags")).isEqualTo(original.fields().get("tags"));
        assertThat(result).isEqualTo(original);
    }

    // -----------------------------------------------------------------------
    // PNumber normalization: equal regardless of the boxed input type
    // -----------------------------------------------------------------------

    @Test
    void pNumber_shouldNormalizeIntegralTypesToLong()
    {
        // setup + execute
        final IProtocolValue.PNumber fromInt = new IProtocolValue.PNumber(3);
        final IProtocolValue.PNumber fromLong = new IProtocolValue.PNumber(3L);

        // verify
        assertThat(fromInt).isEqualTo(fromLong);
        assertThat(fromInt.value()).isInstanceOf(Long.class);
    }

    @Test
    void pNumber_shouldNormalizeFloatingTypesToDouble()
    {
        // setup + execute
        final IProtocolValue.PNumber fromFloat = new IProtocolValue.PNumber(2.5f);
        final IProtocolValue.PNumber fromDouble = new IProtocolValue.PNumber(2.5d);

        // verify
        assertThat(fromFloat).isEqualTo(fromDouble);
        assertThat(fromFloat.value()).isInstanceOf(Double.class);
    }

    // -----------------------------------------------------------------------
    // PRecord field-order preservation, including across a JSON round-trip
    // -----------------------------------------------------------------------

    @Test
    void pRecord_shouldPreserveFieldOrderAcrossRoundTrip() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final Map<String, IProtocolValue> fields = new LinkedHashMap<>();
        fields.put("zeta", new IProtocolValue.PString("z"));
        fields.put("alpha", new IProtocolValue.PString("a"));
        fields.put("mid", new IProtocolValue.PString("m"));
        final IProtocolValue.PRecord original = new IProtocolValue.PRecord(fields);

        // execute
        assertThat(original.fields().keySet()).containsExactly("zeta", "alpha", "mid");
        final String json = mapper.writeValueAsString(original);
        final IProtocolValue deserialized = mapper.readValue(json, IProtocolValue.class);

        // verify
        assertThat(deserialized).isInstanceOf(IProtocolValue.PRecord.class);
        assertThat(((IProtocolValue.PRecord) deserialized).fields().keySet())
            .containsExactly("zeta", "alpha", "mid");
    }

    // -----------------------------------------------------------------------
    // GetCapturedEvents.Response round-trip: the first nested-polymorphic response in the codebase
    // -----------------------------------------------------------------------

    @Test
    void serialize_getCapturedEventsResponse_withTypedEventMap_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final Map<String, IProtocolValue> eventValues = new LinkedHashMap<>();
        eventValues.put("isCancelled", new IProtocolValue.PBool(true));
        eventValues.put("getDamage", new IProtocolValue.PNumber(4.5));
        eventValues.put(
            "getPlayer",
            new IProtocolValue.PRef("org.bukkit.entity.Player", "00000000-0000-0000-0000-000000000003"));
        eventValues.put(
            "getDrops",
            new IProtocolValue.PList(List.of(new IProtocolValue.PString("minecraft:stone"))));
        final GetCapturedEvents.CapturedEvent capturedEvent =
            new GetCapturedEvents.CapturedEvent(5L, eventValues);
        final GetCapturedEvents.Response original = new GetCapturedEvents.Response(List.of(capturedEvent));

        // execute
        final String json = mapper.writeValueAsString(original);
        final GetCapturedEvents.Response result = mapper.readValue(json, GetCapturedEvents.Response.class);

        // verify
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().tick()).isEqualTo(5L);
        assertThat(result.events().getFirst().values()).containsExactlyEntriesOf(eventValues);
        assertThat(result).isEqualTo(original);
    }
}
