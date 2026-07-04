package nl.pim16aap2.lightkeeper.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies round-trip JSON serialization and polymorphic deserialization for {@link IAgentCommand} subtypes,
 * and round-trip serialization for selected {@link IAgentResponse} subtypes.
 */
class AgentCommandSerializationTest
{
    // -----------------------------------------------------------------------
    // Round-trip: MainWorld.Command (no extra fields)
    // -----------------------------------------------------------------------

    @Test
    void serialize_mainWorldCommand_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final MainWorld.Command original = new MainWorld.Command("req-1");

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(MainWorld.Command.class);
        final MainWorld.Command result = (MainWorld.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-1");
    }

    // -----------------------------------------------------------------------
    // Round-trip: TeleportPlayer.Command (UUID + doubles)
    // -----------------------------------------------------------------------

    @Test
    void serialize_teleportPlayerCommand_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        final TeleportPlayer.Command original =
            new TeleportPlayer.Command("req-2", uuid, "world", 1.5, 64.0, -3.25);

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(TeleportPlayer.Command.class);
        final TeleportPlayer.Command result = (TeleportPlayer.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-2");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.worldName()).isEqualTo("world");
        assertThat(result.x()).isEqualTo(1.5);
        assertThat(result.y()).isEqualTo(64.0);
        assertThat(result.z()).isEqualTo(-3.25);
    }

    // -----------------------------------------------------------------------
    // Round-trip: CreatePlayer.Command with null optional fields
    // -----------------------------------------------------------------------

    @Test
    void serialize_createPlayerCommand_withNullOptionals_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        final CreatePlayer.Command original = new CreatePlayer.Command(
            "req-3", "Alice", uuid, "world",
            null, null, null,
            null, null
        );

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(CreatePlayer.Command.class);
        final CreatePlayer.Command result = (CreatePlayer.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-3");
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.worldName()).isEqualTo("world");
        assertThat(result.x()).isNull();
        assertThat(result.y()).isNull();
        assertThat(result.z()).isNull();
        assertThat(result.health()).isNull();
        assertThat(result.permissionsCsv()).isNull();
    }

    // -----------------------------------------------------------------------
    // Round-trip: CreatePlayer.Command with all fields present
    // -----------------------------------------------------------------------

    @Test
    void serialize_createPlayerCommand_withAllFields_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        final CreatePlayer.Command original = new CreatePlayer.Command(
            "req-4", "Bob", uuid, "nether",
            10.0, 64.0, -5.0,
            20.0, "minecraft.command.tp,some.other.node"
        );

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(CreatePlayer.Command.class);
        final CreatePlayer.Command result = (CreatePlayer.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-4");
        assertThat(result.name()).isEqualTo("Bob");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.worldName()).isEqualTo("nether");
        assertThat(result.x()).isEqualTo(10.0);
        assertThat(result.y()).isEqualTo(64.0);
        assertThat(result.z()).isEqualTo(-5.0);
        assertThat(result.health()).isEqualTo(20.0);
        assertThat(result.permissionsCsv()).isEqualTo("minecraft.command.tp,some.other.node");
    }

    // -----------------------------------------------------------------------
    // Round-trip: DragMenuSlots.Command (int[] slots)
    // -----------------------------------------------------------------------

    @Test
    void serialize_dragMenuSlotsCommand_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000004");
        final DragMenuSlots.Command original = new DragMenuSlots.Command(
            "req-5", uuid, "minecraft:diamond", new int[]{0, 4, 8}
        );

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(DragMenuSlots.Command.class);
        final DragMenuSlots.Command result = (DragMenuSlots.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-5");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.materialKey()).isEqualTo("minecraft:diamond");
        assertThat(result.slots()).containsExactly(0, 4, 8);
    }

    // -----------------------------------------------------------------------
    // Deserialization from raw JSON: discriminator field selects subtype
    // -----------------------------------------------------------------------

    @Test
    void deserialize_rawJson_selectsCorrectConcreteType() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final String json = "{\"action\":\"WAIT_TICKS\",\"requestId\":\"req-6\",\"ticks\":20}";

        // execute
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(WaitTicks.Command.class);
        final WaitTicks.Command result = (WaitTicks.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-6");
        assertThat(result.ticks()).isEqualTo(20);
    }

    // -----------------------------------------------------------------------
    // Deserialization from raw JSON: HANDSHAKE subtype
    // -----------------------------------------------------------------------

    @Test
    void deserialize_handshakeJson_selectsHandshakeCommand() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final String json = "{\"action\":\"HANDSHAKE\",\"requestId\":\"req-7\","
            + "\"token\":\"secret\",\"protocolVersion\":3,\"agentSha256\":\"\"}";

        // execute
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(Handshake.Command.class);
        final Handshake.Command result = (Handshake.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-7");
        assertThat(result.token()).isEqualTo("secret");
        assertThat(result.protocolVersion()).isEqualTo(3);
        assertThat(result.agentSha256()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Deserialization from raw JSON: GET_SERVER_PLATFORM subtype
    // -----------------------------------------------------------------------

    @Test
    void deserialize_getServerPlatformJson_selectsGetServerPlatformCommand() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final String json = "{\"action\":\"GET_SERVER_PLATFORM\",\"requestId\":\"req-8\"}";

        // execute
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(GetServerPlatform.Command.class);
        assertThat(deserialized.requestId()).isEqualTo("req-8");
    }

    // -----------------------------------------------------------------------
    // Round-trip: MainWorld.Response
    // -----------------------------------------------------------------------

    @Test
    void serialize_mainWorldResponse_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final MainWorld.Response original = new MainWorld.Response("req-9", "world");

        // execute
        final String json = mapper.writeValueAsString(original);
        final MainWorld.Response result = mapper.readValue(json, MainWorld.Response.class);

        // verify
        assertThat(result.requestId()).isEqualTo("req-9");
        assertThat(result.worldName()).isEqualTo("world");
    }

    // -----------------------------------------------------------------------
    // Round-trip: DropItem.Response
    // -----------------------------------------------------------------------

    @Test
    void serialize_dropItemResponse_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final DropItem.Response original = new DropItem.Response("req-10", true);

        // execute
        final String json = mapper.writeValueAsString(original);
        final DropItem.Response result = mapper.readValue(json, DropItem.Response.class);

        // verify
        assertThat(result.requestId()).isEqualTo("req-10");
        assertThat(result.dropped()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Round-trip: Handshake.Response
    // -----------------------------------------------------------------------

    @Test
    void serialize_handshakeResponse_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final Handshake.Response original = new Handshake.Response("req-11", 3, "1.21.11-R0.1-SNAPSHOT");

        // execute
        final String json = mapper.writeValueAsString(original);
        final Handshake.Response result = mapper.readValue(json, Handshake.Response.class);

        // verify
        assertThat(result.requestId()).isEqualTo("req-11");
        assertThat(result.protocolVersion()).isEqualTo(3);
        assertThat(result.bukkitVersion()).isEqualTo("1.21.11-R0.1-SNAPSHOT");
    }

    // -----------------------------------------------------------------------
    // Guard: @JsonSubTypes registration matches the sealed permits clause
    // -----------------------------------------------------------------------

    @Test
    void jsonSubTypes_registerEveryPermittedCommandSubtype()
    {
        // setup
        final Set<Class<?>> permittedSubtypes = Set.of(IAgentCommand.class.getPermittedSubclasses());
        final JsonSubTypes jsonSubTypes = IAgentCommand.class.getAnnotation(JsonSubTypes.class);

        // execute
        final Set<Class<?>> registeredSubtypes = Arrays.stream(jsonSubTypes.value())
            .map(JsonSubTypes.Type::value)
            .collect(Collectors.toSet());

        // verify
        // A command that is registered in the permits clause and the dispatcher switch (both compiler-checked)
        // but omitted from @JsonSubTypes compiles and passes per-type tests, yet fails polymorphic
        // deserialization at runtime. Asserting set-equality here turns that drift into a test failure.
        assertThat(registeredSubtypes).isEqualTo(permittedSubtypes);
    }
}
