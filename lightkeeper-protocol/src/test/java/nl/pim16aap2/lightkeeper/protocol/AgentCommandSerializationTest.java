package nl.pim16aap2.lightkeeper.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies round-trip JSON serialization and polymorphic deserialization for {@link IAgentCommand} subtypes.
 */
class AgentCommandSerializationTest
{
    // -----------------------------------------------------------------------
    // Round-trip: MainWorldCommand (no extra fields)
    // -----------------------------------------------------------------------

    @Test
    void serialize_mainWorldCommand_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final MainWorldCommand original = new MainWorldCommand("req-1");

        // execute
        final String json = mapper.writeValueAsString(original);
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(MainWorldCommand.class);
        final MainWorldCommand result = (MainWorldCommand) deserialized;
        assertThat(result.requestId()).isEqualTo("req-1");
    }

    // -----------------------------------------------------------------------
    // Round-trip: TeleportPlayerCommand (UUID + doubles)
    // -----------------------------------------------------------------------

    @Test
    void serialize_teleportPlayerCommand_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        final TeleportPlayerCommand original = new TeleportPlayerCommand("req-2", uuid, "world", 1.5, 64.0, -3.25);

        // execute
        final String json = mapper.writeValueAsString(original);
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(TeleportPlayerCommand.class);
        final TeleportPlayerCommand result = (TeleportPlayerCommand) deserialized;
        assertThat(result.requestId()).isEqualTo("req-2");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.worldName()).isEqualTo("world");
        assertThat(result.x()).isEqualTo(1.5);
        assertThat(result.y()).isEqualTo(64.0);
        assertThat(result.z()).isEqualTo(-3.25);
    }

    // -----------------------------------------------------------------------
    // Round-trip: CreatePlayerCommand with null optional fields
    // -----------------------------------------------------------------------

    @Test
    void serialize_createPlayerCommand_withNullOptionals_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        final CreatePlayerCommand original = new CreatePlayerCommand(
            "req-3", "Alice", uuid, "world",
            null, null, null,
            null, null
        );

        // execute
        final String json = mapper.writeValueAsString(original);
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(CreatePlayerCommand.class);
        final CreatePlayerCommand result = (CreatePlayerCommand) deserialized;
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
    // Round-trip: CreatePlayerCommand with all fields present
    // -----------------------------------------------------------------------

    @Test
    void serialize_createPlayerCommand_withAllFields_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        final CreatePlayerCommand original = new CreatePlayerCommand(
            "req-4", "Bob", uuid, "nether",
            10.0, 64.0, -5.0,
            20.0, "minecraft.command.tp,some.other.node"
        );

        // execute
        final String json = mapper.writeValueAsString(original);
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(CreatePlayerCommand.class);
        final CreatePlayerCommand result = (CreatePlayerCommand) deserialized;
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
    // Round-trip: DragMenuSlotsCommand (int[] slots)
    // -----------------------------------------------------------------------

    @Test
    void serialize_dragMenuSlotsCommand_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = new ObjectMapper();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000004");
        final DragMenuSlotsCommand original = new DragMenuSlotsCommand(
            "req-5", uuid, "minecraft:diamond", new int[]{0, 4, 8}
        );

        // execute
        final String json = mapper.writeValueAsString(original);
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(DragMenuSlotsCommand.class);
        final DragMenuSlotsCommand result = (DragMenuSlotsCommand) deserialized;
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
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(WaitTicksCommand.class);
        final WaitTicksCommand result = (WaitTicksCommand) deserialized;
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
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(HandshakeCommand.class);
        final HandshakeCommand result = (HandshakeCommand) deserialized;
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
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(GetServerPlatformCommand.class);
        assertThat(deserialized.requestId()).isEqualTo("req-8");
    }
}
