package nl.pim16aap2.lightkeeper.framework.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.WaitTicksCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UdsAgentClientTest
{
    private static final ObjectMapper REQUEST_MAPPER = new ObjectMapper();
    private static final UUID PLAYER_ID =
        UUID.fromString("6efa93e0-6b5f-45b7-8af8-2453c9c7ef0c");

    @Test
    void send_shouldThrowExceptionWhenResponseIdDoesNotMatch(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-mismatch.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, new AgentResponse(
            "unexpected-request-id",
            true,
            null,
            null,
            Map.of()
        )))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute + verify
            assertThatThrownBy(() -> client.send(new WaitTicksCommand("1", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected response id");
            client.close();
        }
    }

    @Test
    void send_shouldIncludeProtocolVersionDetailsWhenAgentReportsFailure(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-failure.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, new AgentResponse(
            "1",
            false,
            "PROTOCOL_MISMATCH",
            "Protocol mismatch.",
            Map.of(
                "expectedProtocolVersion", "7",
                "actualProtocolVersion", "6"
            )
        )))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute + verify
            assertThatThrownBy(() -> client.send(new WaitTicksCommand("1", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("code=PROTOCOL_MISMATCH")
                .hasMessageContaining("expectedProtocolVersion=7")
                .hasMessageContaining("actualProtocolVersion=6");
            client.close();
        }
    }

    @Test
    void serverPlatform_shouldSendPlatformRequestAndMapPaperResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-platform.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("platform", "paper"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final nl.pim16aap2.lightkeeper.framework.Platform result = client.serverPlatform();

            // verify
            assertThat(result).isEqualTo(nl.pim16aap2.lightkeeper.framework.Platform.PAPER);
            assertRequestAction(server, "GET_SERVER_PLATFORM");
        }
    }

    @Test
    void serverPlatform_shouldMapSpigotResponseToSpigot(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-platform-spigot.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("platform", "spigot"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final nl.pim16aap2.lightkeeper.framework.Platform result = client.serverPlatform();

            // verify
            assertThat(result).isEqualTo(nl.pim16aap2.lightkeeper.framework.Platform.SPIGOT);
            assertRequestAction(server, "GET_SERVER_PLATFORM");
        }
    }

    @Test
    void serverPlatform_shouldMapUnknownPlatformToUnknown(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-platform-unknown.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("platform", "sponge"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final nl.pim16aap2.lightkeeper.framework.Platform result = client.serverPlatform();

            // verify
            assertThat(result).isEqualTo(nl.pim16aap2.lightkeeper.framework.Platform.UNKNOWN);
        }
    }

    @Test
    void loadChunk_shouldSendChunkCoordinates(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-load-chunk.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("loaded", "true"))
        );
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean result = client.loadChunk("world", 3, -4);

            // verify
            assertThat(result).isTrue();
            final JsonNode request = REQUEST_MAPPER.readTree(server.requestLine());
            assertThat(request.get("action").asText()).isEqualTo("LOAD_CHUNK");
            assertThat(request.get("worldName").asText()).isEqualTo("world");
            assertThat(request.get("x").asInt()).isEqualTo(3);
            assertThat(request.get("z").asInt()).isEqualTo(-4);
        }
    }

    @Test
    void getPlayerInventory_shouldParseInventoryItemMaps(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-inventory.sock");
        final String inventoryJson =
            "[{\"slot\":1,\"materialKey\":\"minecraft:stone\",\"displayName\":\"Stone\",\"lore\":[\"Line\"]}]";
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("inventoryJson", inventoryJson))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final List<Map<String, Object>> result = client.getPlayerInventory(PLAYER_ID);

            // verify
            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsEntry("materialKey", "minecraft:stone");
            assertRequestAction(server, "GET_PLAYER_INVENTORY");
        }
    }

    @Test
    void dropItem_shouldParseDroppedState(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-drop.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("dropped", "true"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean result = client.dropItem(PLAYER_ID);

            // verify
            assertThat(result).isTrue();
            assertRequestAction(server, "DROP_ITEM");
        }
    }

    @Test
    void playerChatComponents_shouldParseComponentSnapshots(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-components.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("componentsJson", "[\"{\\\"text\\\":\\\"Hi\\\"}\"]"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final List<nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot> result =
                client.playerChatComponents(PLAYER_ID);

            // verify
            assertThat(result).extracting(nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot::json)
                .containsExactly("{\"text\":\"Hi\"}");
            assertRequestAction(server, "GET_PLAYER_CHAT_COMPONENTS");
        }
    }

    @Test
    void getCapturedEvents_shouldSendEventClassNameAndParseEvents(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-events.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("eventsJson", "[{\"getValue\":\"value\"}]"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final List<Map<String, String>> result = client.getCapturedEvents("org.bukkit.event.Event");

            // verify
            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsEntry("getValue", "value");
            final JsonNode request = REQUEST_MAPPER.readTree(server.requestLine());
            assertThat(request.get("action").asText()).isEqualTo("GET_CAPTURED_EVENTS");
            assertThat(request.get("eventClassName").asText()).isEqualTo("org.bukkit.event.Event");
        }
    }

    @Test
    void handshake_shouldSendHandshakeRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-handshake.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("protocolVersion", "1", "bukkitVersion", "1.21.11"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.handshake("my-token", 1, "sha256");

            // verify
            final JsonNode request = REQUEST_MAPPER.readTree(server.requestLine());
            assertThat(request.get("action").asText()).isEqualTo("HANDSHAKE");
            assertThat(request.get("token").asText()).isEqualTo("my-token");
            assertThat(request.get("protocolVersion").asInt()).isEqualTo(1);
            assertThat(request.get("agentSha256").asText()).isEqualTo("sha256");
        }
    }

    @Test
    void mainWorld_shouldSendRequestAndReturnWorldName(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-mainworld.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of("worldName", "world")));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final String result = client.mainWorld();

            // verify
            assertThat(result).isEqualTo("world");
            assertRequestAction(server, "MAIN_WORLD");
        }
    }

    @Test
    void unloadChunk_shouldSendCoordinatesAndReturnResult(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-unload-chunk.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of("unloaded", "false")));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean result = client.unloadChunk("world", 5, -3);

            // verify
            assertThat(result).isFalse();
            final JsonNode request = REQUEST_MAPPER.readTree(server.requestLine());
            assertThat(request.get("action").asText()).isEqualTo("UNLOAD_CHUNK");
            assertThat(request.get("worldName").asText()).isEqualTo("world");
            assertThat(request.get("x").asInt()).isEqualTo(5);
            assertThat(request.get("z").asInt()).isEqualTo(-3);
        }
    }

    @Test
    void isChunkLoaded_shouldSendCoordinatesAndReturnResult(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-is-loaded.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of("loaded", "true")));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean result = client.isChunkLoaded("world", 1, 2);

            // verify
            assertThat(result).isTrue();
            assertRequestAction(server, "IS_CHUNK_LOADED");
        }
    }

    @Test
    void executeCommand_shouldSendCommandAndReturnResult(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-execute-cmd.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of("success", "true")));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean result = client.executeCommand(
                nl.pim16aap2.lightkeeper.framework.CommandSource.CONSOLE, "time set day");

            // verify
            assertThat(result).isTrue();
            final JsonNode request = REQUEST_MAPPER.readTree(server.requestLine());
            assertThat(request.get("action").asText()).isEqualTo("EXECUTE_COMMAND");
            assertThat(request.get("commandSource").asText()).isEqualTo("CONSOLE");
            assertThat(request.get("command").asText()).isEqualTo("time set day");
        }
    }

    @Test
    void registerEventListener_shouldSendEventClassName(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-register-event.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of()));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.registerEventListener("org.bukkit.event.player.PlayerJoinEvent");

            // verify
            final JsonNode request = REQUEST_MAPPER.readTree(server.requestLine());
            assertThat(request.get("action").asText()).isEqualTo("REGISTER_EVENT_LISTENER");
            assertThat(request.get("eventClassName").asText()).isEqualTo("org.bukkit.event.player.PlayerJoinEvent");
        }
    }

    @Test
    void clearCapturedEvents_shouldSendEventClassName(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-clear-events.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of()));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.clearCapturedEvents("org.bukkit.event.player.PlayerJoinEvent");

            // verify
            final JsonNode request = REQUEST_MAPPER.readTree(server.requestLine());
            assertThat(request.get("action").asText()).isEqualTo("CLEAR_CAPTURED_EVENTS");
            assertThat(request.get("eventClassName").asText())
                .isEqualTo("org.bukkit.event.player.PlayerJoinEvent");
        }
    }

    @Test
    void unregisterEventListener_shouldSendEventClassName(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-unregister-event.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of()));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.unregisterEventListener("org.bukkit.event.player.PlayerJoinEvent");

            // verify
            final JsonNode request = REQUEST_MAPPER.readTree(server.requestLine());
            assertThat(request.get("action").asText()).isEqualTo("UNREGISTER_EVENT_LISTENER");
            assertThat(request.get("eventClassName").asText())
                .isEqualTo("org.bukkit.event.player.PlayerJoinEvent");
        }
    }

    @Test
    void waitTicks_shouldSendTickCount(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-wait-ticks.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("startTick", "0", "endTick", "5"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.waitTicks(5);

            // verify
            final JsonNode request = REQUEST_MAPPER.readTree(server.requestLine());
            assertThat(request.get("action").asText()).isEqualTo("WAIT_TICKS");
            assertThat(request.get("ticks").asInt()).isEqualTo(5);
        }
    }

    @Test
    void playerMessages_shouldParseMessageList(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-messages.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("messagesJson", "[\"hello\",\"world\"]"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final List<String> result = client.playerMessages(PLAYER_ID);

            // verify
            assertThat(result).containsExactly("hello", "world");
            assertRequestAction(server, "GET_PLAYER_MESSAGES");
        }
    }

    private static AgentResponse successResponse(Map<String, String> data)
    {
        return new AgentResponse("1", true, null, null, data);
    }

    /**
     * Asserts that the received request JSON has the expected {@code "action"} field value.
     *
     * @param server
     *     The test socket server holding the received request line.
     * @param expectedAction
     *     The expected action name (e.g. {@code "LOAD_CHUNK"}).
     * @throws IOException
     *     If JSON parsing fails.
     */
    private static void assertRequestAction(AgentSocketServer server, String expectedAction)
        throws IOException
    {
        final JsonNode request = REQUEST_MAPPER.readTree(server.requestLine());
        assertThat(request.get("action").asText()).isEqualTo(expectedAction);
    }

    private static final class AgentSocketServer implements AutoCloseable
    {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final ServerSocketChannel serverChannel;
        private final Thread workerThread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        private final AtomicReference<String> requestLine = new AtomicReference<>();
        private final Path socketPath;

        private AgentSocketServer(Path socketPath, AgentResponse response)
            throws IOException
        {
            this.socketPath = socketPath;
            this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
            this.workerThread = new Thread(() -> serveSingleResponse(response), "uds-agent-client-test-server");
            started.countDown();
            this.workerThread.start();
        }

        private static AgentSocketServer start(Path socketPath, AgentResponse response)
            throws IOException, InterruptedException
        {
            final AgentSocketServer server = new AgentSocketServer(socketPath, response);
            if (!server.started.await(3, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out while waiting for test socket server startup.");
            return server;
        }

        private void serveSingleResponse(AgentResponse response)
        {
            try (SocketChannel clientChannel = serverChannel.accept();
                 BufferedReader reader = new BufferedReader(
                     Channels.newReader(clientChannel, StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(
                     Channels.newWriter(clientChannel, StandardCharsets.UTF_8)))
            {
                final String line = reader.readLine();
                if (line == null)
                    return;
                this.requestLine.set(line);
                writer.write(OBJECT_MAPPER.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            }
            catch (Throwable throwable)
            {
                workerFailure.set(throwable);
            }
        }

        @Override
        public void close()
            throws Exception
        {
            serverChannel.close();
            workerThread.join(TimeUnit.SECONDS.toMillis(3));
            final Throwable failure = workerFailure.get();
            if (failure != null)
                throw new IllegalStateException("Socket server worker failed.", failure);
            Files.deleteIfExists(socketPath);
        }

        private String requestLine()
        {
            return java.util.Objects.requireNonNull(requestLine.get(), "No request was received.");
        }
    }
}
