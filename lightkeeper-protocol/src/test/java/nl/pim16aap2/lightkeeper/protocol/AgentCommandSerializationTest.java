package nl.pim16aap2.lightkeeper.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
        final ObjectMapper mapper = AgentProtocolMapper.create();
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
        final ObjectMapper mapper = AgentProtocolMapper.create();
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
        final ObjectMapper mapper = AgentProtocolMapper.create();
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
        final ObjectMapper mapper = AgentProtocolMapper.create();
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
        final ObjectMapper mapper = AgentProtocolMapper.create();
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
    // Round-trip: MutatePlayerPermission.Command (one per Mode value)
    // -----------------------------------------------------------------------

    @Test
    void serialize_mutatePlayerPermissionCommand_withGrantMode_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000005");
        final MutatePlayerPermission.Command original = new MutatePlayerPermission.Command(
            "req-11", uuid, "some.permission", MutatePlayerPermission.Mode.GRANT);

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(MutatePlayerPermission.Command.class);
        final MutatePlayerPermission.Command result = (MutatePlayerPermission.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-11");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.permission()).isEqualTo("some.permission");
        assertThat(result.mode()).isEqualTo(MutatePlayerPermission.Mode.GRANT);
    }

    @Test
    void serialize_mutatePlayerPermissionCommand_withRevokeMode_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000006");
        final MutatePlayerPermission.Command original = new MutatePlayerPermission.Command(
            "req-12", uuid, "some.permission", MutatePlayerPermission.Mode.REVOKE);

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(MutatePlayerPermission.Command.class);
        final MutatePlayerPermission.Command result = (MutatePlayerPermission.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-12");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.permission()).isEqualTo("some.permission");
        assertThat(result.mode()).isEqualTo(MutatePlayerPermission.Mode.REVOKE);
    }

    @Test
    void serialize_mutatePlayerPermissionCommand_withUnsetMode_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000007");
        final MutatePlayerPermission.Command original = new MutatePlayerPermission.Command(
            "req-13", uuid, "some.permission", MutatePlayerPermission.Mode.UNSET);

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(MutatePlayerPermission.Command.class);
        final MutatePlayerPermission.Command result = (MutatePlayerPermission.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-13");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.permission()).isEqualTo("some.permission");
        assertThat(result.mode()).isEqualTo(MutatePlayerPermission.Mode.UNSET);
    }

    // -----------------------------------------------------------------------
    // Round-trip: HasPlayerPermission.Command
    // -----------------------------------------------------------------------

    @Test
    void serialize_hasPlayerPermissionCommand_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000008");
        final HasPlayerPermission.Command original =
            new HasPlayerPermission.Command("req-14", uuid, "some.permission");

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(HasPlayerPermission.Command.class);
        final HasPlayerPermission.Command result = (HasPlayerPermission.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-14");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.permission()).isEqualTo("some.permission");
    }

    // -----------------------------------------------------------------------
    // Round-trip: CancelNextEvents.Command
    // -----------------------------------------------------------------------

    @Test
    void serialize_cancelNextEventsCommand_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final CancelNextEvents.Command original = new CancelNextEvents.Command(
            "req-15", "org.bukkit.event.player.PlayerMoveEvent", 3);

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(CancelNextEvents.Command.class);
        final CancelNextEvents.Command result = (CancelNextEvents.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-15");
        assertThat(result.eventClassName()).isEqualTo("org.bukkit.event.player.PlayerMoveEvent");
        assertThat(result.count()).isEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // Round-trip: PlayerChat.Command
    // -----------------------------------------------------------------------

    @Test
    void serialize_playerChatCommand_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000009");
        final PlayerChat.Command original = new PlayerChat.Command("req-16", uuid, "hello world");

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(PlayerChat.Command.class);
        final PlayerChat.Command result = (PlayerChat.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-16");
        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.message()).isEqualTo("hello world");
    }

    // -----------------------------------------------------------------------
    // Round-trip: CancelNextEvents.Response / PlayerChat.Response (empty records)
    // -----------------------------------------------------------------------

    @Test
    void serialize_cancelNextEventsResponse_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final CancelNextEvents.Response original = new CancelNextEvents.Response();

        // execute
        final String json = mapper.writeValueAsString(original);
        final CancelNextEvents.Response result = mapper.readValue(json, CancelNextEvents.Response.class);

        // verify
        assertThat(result).isEqualTo(original);
    }

    @Test
    void serialize_playerChatResponse_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final PlayerChat.Response original = new PlayerChat.Response();

        // execute
        final String json = mapper.writeValueAsString(original);
        final PlayerChat.Response result = mapper.readValue(json, PlayerChat.Response.class);

        // verify
        assertThat(result).isEqualTo(original);
    }

    // -----------------------------------------------------------------------
    // Round-trip: QueryEntities.Command (bounded, with type filter)
    // -----------------------------------------------------------------------

    @Test
    void serialize_queryEntitiesCommand_boundedWithTypeFilter_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final QueryEntities.Command original = new QueryEntities.Command(
            "req-17", "world", "minecraft:block_display", true,
            0, 1, 2, 10, 11, 12, false
        );

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(QueryEntities.Command.class);
        final QueryEntities.Command result = (QueryEntities.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-17");
        assertThat(result.worldName()).isEqualTo("world");
        assertThat(result.entityTypeKey()).isEqualTo("minecraft:block_display");
        assertThat(result.bounded()).isTrue();
        assertThat(result.minX()).isEqualTo(0);
        assertThat(result.minY()).isEqualTo(1);
        assertThat(result.minZ()).isEqualTo(2);
        assertThat(result.maxX()).isEqualTo(10);
        assertThat(result.maxY()).isEqualTo(11);
        assertThat(result.maxZ()).isEqualTo(12);
        assertThat(result.countOnly()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Round-trip: QueryEntities.Command (unbounded, no type filter, count-only)
    // -----------------------------------------------------------------------

    @Test
    void serialize_queryEntitiesCommand_unboundedWithNullTypeFilter_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final QueryEntities.Command original = new QueryEntities.Command(
            "req-18", "world", null, false,
            0, 0, 0, 0, 0, 0, true
        );

        // execute
        final String json = mapper.writeValueAsString(original);
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(QueryEntities.Command.class);
        final QueryEntities.Command result = (QueryEntities.Command) deserialized;
        assertThat(result.requestId()).isEqualTo("req-18");
        assertThat(result.entityTypeKey()).isNull();
        assertThat(result.bounded()).isFalse();
        assertThat(result.countOnly()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Round-trip: QueryEntities.Response with nested EntityData and TransformData
    // -----------------------------------------------------------------------

    @Test
    void serialize_queryEntitiesResponse_withNestedEntityDataAndTransformData_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final UUID entityUuid = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        final QueryEntities.TransformData transformData = new QueryEntities.TransformData(
            1.0, 2.0, 3.0,
            1.5, 1.5, 1.5,
            List.of(0.1, 0.2, 0.3, 0.4),
            List.of(0.5, 0.6, 0.7, 0.8)
        );
        final QueryEntities.EntityData entityData = new QueryEntities.EntityData(
            entityUuid, "minecraft:block_display", 10.0, 64.0, -5.0,
            "Display Name", List.of("plugin:alpha", "plugin:beta"), transformData
        );
        final QueryEntities.Response original = new QueryEntities.Response(42L, 1, List.of(entityData));

        // execute
        final String json = mapper.writeValueAsString(original);
        final QueryEntities.Response result = mapper.readValue(json, QueryEntities.Response.class);

        // verify
        assertThat(result.tick()).isEqualTo(42L);
        assertThat(result.count()).isEqualTo(1);
        assertThat(result.entities()).containsExactly(entityData);
        assertThat(result).isEqualTo(original);
    }

    // -----------------------------------------------------------------------
    // Round-trip: QueryEntities.Response with a null-transform entity and empty entity list
    // -----------------------------------------------------------------------

    @Test
    void serialize_queryEntitiesResponse_withNullTransformEntity_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final UUID entityUuid = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        final QueryEntities.EntityData entityData = new QueryEntities.EntityData(
            entityUuid, "minecraft:zombie", 1.0, 2.0, 3.0, null, List.of(), null
        );
        final QueryEntities.Response original = new QueryEntities.Response(7L, 1, List.of(entityData));

        // execute
        final String json = mapper.writeValueAsString(original);
        final QueryEntities.Response result = mapper.readValue(json, QueryEntities.Response.class);

        // verify
        assertThat(result.entities()).singleElement().satisfies(entity ->
        {
            assertThat(entity.customName()).isNull();
            assertThat(entity.transform()).isNull();
            assertThat(entity.pdcKeys()).isEmpty();
        });
    }

    @Test
    void serialize_queryEntitiesResponse_withEmptyEntities_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final QueryEntities.Response original = new QueryEntities.Response(3L, 0, List.of());

        // execute
        final String json = mapper.writeValueAsString(original);
        final QueryEntities.Response result = mapper.readValue(json, QueryEntities.Response.class);

        // verify
        assertThat(result.tick()).isEqualTo(3L);
        assertThat(result.count()).isZero();
        assertThat(result.entities()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Deserialization from raw JSON: discriminator field selects subtype
    // -----------------------------------------------------------------------

    @Test
    void deserialize_rawJson_selectsCorrectConcreteType() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
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

    @Test
    void deserialize_shouldRejectNullPrimitiveCommandField()
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final String json = "{\"action\":\"WAIT_TICKS\",\"requestId\":\"request-null\",\"ticks\":null}";

        // execute
        final Throwable throwable = catchThrowable(() -> mapper.readValue(json, IAgentCommand.class));

        // verify
        assertThat(throwable).isInstanceOf(JacksonException.class);
    }

    // -----------------------------------------------------------------------
    // Deserialization from raw JSON: HANDSHAKE subtype
    // -----------------------------------------------------------------------

    @Test
    void deserialize_handshakeJson_selectsHandshakeCommand() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
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
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final String json = "{\"action\":\"GET_SERVER_PLATFORM\",\"requestId\":\"req-8\"}";

        // execute
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(GetServerPlatform.Command.class);
        assertThat(deserialized.requestId()).isEqualTo("req-8");
    }

    // -----------------------------------------------------------------------
    // Deserialization from raw JSON: GET_SERVER_ERRORS / CLEAR_SERVER_ERRORS subtypes
    // -----------------------------------------------------------------------

    @Test
    void deserialize_getServerErrorsJson_selectsGetServerErrorsCommand() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final String json = "{\"action\":\"GET_SERVER_ERRORS\",\"requestId\":\"req-9\"}";

        // execute
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(GetServerErrors.Command.class);
        assertThat(deserialized.requestId()).isEqualTo("req-9");
    }

    @Test
    void deserialize_clearServerErrorsJson_selectsClearServerErrorsCommand() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final String json = "{\"action\":\"CLEAR_SERVER_ERRORS\",\"requestId\":\"req-10\"}";

        // execute
        @SuppressWarnings("rawtypes")
        final IAgentCommand deserialized = mapper.readValue(json, IAgentCommand.class);

        // verify
        assertThat(deserialized).isInstanceOf(ClearServerErrors.Command.class);
        assertThat(deserialized.requestId()).isEqualTo("req-10");
    }

    @Test
    void serialize_clearServerErrorsResponse_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final ClearServerErrors.Response original = new ClearServerErrors.Response();

        // execute
        final String json = mapper.writeValueAsString(original);
        final ClearServerErrors.Response result = mapper.readValue(json, ClearServerErrors.Response.class);

        // verify
        assertThat(result).isEqualTo(original);
    }

    // -----------------------------------------------------------------------
    // Round-trip: GetServerErrors.Response with and without throwable metadata
    // -----------------------------------------------------------------------

    @Test
    void serialize_getServerErrorsResponse_withThrowableEntry_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final ServerErrorEntry entry = new ServerErrorEntry(
            1_720_000_000_000L,
            "ERROR",
            "ERROR",
            "net.example.SomePlugin",
            "Server thread",
            "Could not pass event to plugin",
            "java.lang.IllegalStateException",
            "boom",
            List.of("java.lang.IllegalStateException: boom", "\tat net.example.SomePlugin.onEvent(SomePlugin.java:1)")
        );
        final GetServerErrors.Response original = new GetServerErrors.Response(List.of(entry), 5L, true);

        // execute
        final String json = mapper.writeValueAsString(original);
        final GetServerErrors.Response result = mapper.readValue(json, GetServerErrors.Response.class);

        // verify
        assertThat(result.droppedCount()).isEqualTo(5L);
        assertThat(result.captureActive()).isTrue();
        assertThat(result.errors()).containsExactly(entry);
    }

    @Test
    void serialize_getServerErrorsResponse_withoutThrowableEntry_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final ServerErrorEntry entry = new ServerErrorEntry(
            1_720_000_000_001L,
            "WARNING",
            "WARN",
            "net.minecraft.server",
            "Worker-1",
            "Something looked off",
            null,
            null,
            List.of()
        );
        final GetServerErrors.Response original = new GetServerErrors.Response(List.of(entry), 0L, false);

        // execute
        final String json = mapper.writeValueAsString(original);
        final GetServerErrors.Response result = mapper.readValue(json, GetServerErrors.Response.class);

        // verify
        assertThat(result.droppedCount()).isZero();
        assertThat(result.captureActive()).isFalse();
        assertThat(result.errors()).containsExactly(entry);
        assertThat(result.errors().getFirst().throwableClass()).isNull();
        assertThat(result.errors().getFirst().throwableMessage()).isNull();
    }

    // -----------------------------------------------------------------------
    // Round-trip: MainWorld.Response
    // -----------------------------------------------------------------------

    @Test
    void serialize_mainWorldResponse_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final MainWorld.Response original = new MainWorld.Response("world");

        // execute
        final String json = mapper.writeValueAsString(original);
        final MainWorld.Response result = mapper.readValue(json, MainWorld.Response.class);

        // verify
        assertThat(result.worldName()).isEqualTo("world");
    }

    // -----------------------------------------------------------------------
    // Round-trip: DropItem.Response
    // -----------------------------------------------------------------------

    @Test
    void serialize_dropItemResponse_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final DropItem.Response original = new DropItem.Response(DropResult.DROPPED);

        // execute
        final String json = mapper.writeValueAsString(original);
        final DropItem.Response result = mapper.readValue(json, DropItem.Response.class);

        // verify
        assertThat(result.result()).isEqualTo(DropResult.DROPPED);
    }

    @Test
    void serialize_dropItemResponse_withCancelledResult_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final DropItem.Response original = new DropItem.Response(DropResult.CANCELLED);

        // execute
        final String json = mapper.writeValueAsString(original);
        final DropItem.Response result = mapper.readValue(json, DropItem.Response.class);

        // verify
        assertThat(result.result()).isEqualTo(DropResult.CANCELLED);
    }

    @Test
    void serialize_dropItemResponse_withEmptyHandResult_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final DropItem.Response original = new DropItem.Response(DropResult.EMPTY_HAND);

        // execute
        final String json = mapper.writeValueAsString(original);
        final DropItem.Response result = mapper.readValue(json, DropItem.Response.class);

        // verify
        assertThat(result.result()).isEqualTo(DropResult.EMPTY_HAND);
    }

    // -----------------------------------------------------------------------
    // Round-trip: HasPlayerPermission.Response
    // -----------------------------------------------------------------------

    @Test
    void serialize_hasPlayerPermissionResponse_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final HasPlayerPermission.Response original = new HasPlayerPermission.Response(true);

        // execute
        final String json = mapper.writeValueAsString(original);
        final HasPlayerPermission.Response result = mapper.readValue(json, HasPlayerPermission.Response.class);

        // verify
        assertThat(result.value()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Round-trip: Handshake.Response
    // -----------------------------------------------------------------------

    @Test
    void serialize_handshakeResponse_roundTrips() throws Exception
    {
        // setup
        final ObjectMapper mapper = AgentProtocolMapper.create();
        final Handshake.Response original = new Handshake.Response(3, "1.21.11-R0.1-SNAPSHOT");

        // execute
        final String json = mapper.writeValueAsString(original);
        final Handshake.Response result = mapper.readValue(json, Handshake.Response.class);

        // verify
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

    @Test
    void jsonSubTypes_useUniqueDiscriminatorNames()
    {
        // setup
        final JsonSubTypes jsonSubTypes = IAgentCommand.class.getAnnotation(JsonSubTypes.class);

        // execute
        final List<String> discriminatorNames = Arrays.stream(jsonSubTypes.value())
            .map(JsonSubTypes.Type::name)
            .toList();

        // verify — a duplicated discriminator name compiles and only fails at runtime deserialization
        assertThat(discriminatorNames).doesNotHaveDuplicates();
    }

    @Test
    void permittedCommands_declareResponseTypeMatchingSiblingResponse()
    {
        // setup + execute + verify — the IAgentCommand<R> type argument must be the Response nested in the same
        // namespace class, so a command paired with the wrong response record fails here rather than silently
        // deserializing into the wrong shape on the client.
        for (final Class<?> commandClass : IAgentCommand.class.getPermittedSubclasses())
        {
            final Type responseTypeArgument = resolveAgentCommandTypeArgument(commandClass);
            final Class<?> enclosing = commandClass.getEnclosingClass();
            assertThat(enclosing)
                .as("%s must be a nested Command record", commandClass.getSimpleName())
                .isNotNull();

            final Class<?> siblingResponse = siblingResponse(enclosing);
            assertThat(responseTypeArgument)
                .as("IAgentCommand type argument for %s", commandClass.getName())
                .isEqualTo(siblingResponse);
        }
    }

    private static Type resolveAgentCommandTypeArgument(Class<?> commandClass)
    {
        for (final Type genericInterface : commandClass.getGenericInterfaces())
        {
            if (genericInterface instanceof ParameterizedType parameterized
                && parameterized.getRawType() == IAgentCommand.class)
            {
                return parameterized.getActualTypeArguments()[0];
            }
        }
        throw new AssertionError(commandClass.getName() + " does not implement IAgentCommand<R> directly.");
    }

    private static Class<?> siblingResponse(Class<?> enclosing)
    {
        try
        {
            return Class.forName(enclosing.getName() + "$Response", false, enclosing.getClassLoader());
        }
        catch (ClassNotFoundException exception)
        {
            throw new AssertionError("No sibling Response record for " + enclosing.getName(), exception);
        }
    }
}
