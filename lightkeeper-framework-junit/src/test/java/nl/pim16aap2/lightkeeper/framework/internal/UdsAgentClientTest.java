package nl.pim16aap2.lightkeeper.framework.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UdsAgentClientTest
{
    private static final ObjectMapper REQUEST_MAPPER = new ObjectMapper();
    private static final java.util.UUID PLAYER_ID =
        java.util.UUID.fromString("6efa93e0-6b5f-45b7-8af8-2453c9c7ef0c");

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
            assertThatThrownBy(() -> client.send(AgentAction.WAIT_TICKS, Map.of("ticks", "1")))
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
            assertThatThrownBy(() -> client.send(AgentAction.WAIT_TICKS, Map.of("ticks", "1")))
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
            successResponse(Map.of("platform", "Paper 1.21.11"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final nl.pim16aap2.lightkeeper.framework.Platform result = client.serverPlatform();

            // verify
            assertThat(result).isEqualTo(nl.pim16aap2.lightkeeper.framework.Platform.PAPER);
            assertRequest(server, AgentAction.GET_SERVER_PLATFORM, Map.of());
        }
    }

    @Test
    void serverPlatform_shouldMapCraftBukkitResponseToSpigot(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-platform-craftbukkit.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("platform", "CraftBukkit"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final nl.pim16aap2.lightkeeper.framework.Platform result = client.serverPlatform();

            // verify
            assertThat(result).isEqualTo(nl.pim16aap2.lightkeeper.framework.Platform.SPIGOT);
            assertRequest(server, AgentAction.GET_SERVER_PLATFORM, Map.of());
        }
    }

    @Test
    void loadChunk_shouldSendChunkCoordinates(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-load-chunk.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of()));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.loadChunk("world", 3, -4);

            // verify
            assertRequest(server, AgentAction.LOAD_CHUNK, Map.of("worldName", "world", "x", "3", "z", "-4"));
        }
    }

    @Test
    void playerInventory_shouldParseInventorySnapshot(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-inventory.sock");
        final String inventoryJson = "[{\"slot\":1,\"materialKey\":\"minecraft:stone\",\"displayName\":\"Stone\","
            + "\"lore\":[\"Line\"]}]";
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("inventoryJson", inventoryJson))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final nl.pim16aap2.lightkeeper.framework.InventorySnapshot result = client.playerInventory(PLAYER_ID);

            // verify
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().getFirst().materialKey()).isEqualTo("minecraft:stone");
            assertRequest(server, AgentAction.GET_PLAYER_INVENTORY, Map.of("uuid", PLAYER_ID.toString()));
        }
    }

    @Test
    void dropItem_shouldParseCancellationState(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-drop.sock");
        try (AgentSocketServer server = AgentSocketServer.start(
            socketPath,
            successResponse(Map.of("cancelled", "true"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean result = client.dropItem(PLAYER_ID);

            // verify
            assertThat(result).isTrue();
            assertRequest(server, AgentAction.DROP_ITEM, Map.of("uuid", PLAYER_ID.toString()));
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
            final java.util.List<nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot> result =
                client.playerChatComponents(PLAYER_ID);

            // verify
            assertThat(result).extracting(nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot::json)
                .containsExactly("{\"text\":\"Hi\"}");
            assertRequest(server, AgentAction.GET_PLAYER_CHAT_COMPONENTS, Map.of("uuid", PLAYER_ID.toString()));
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
            final java.util.List<nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot> result =
                client.getCapturedEvents("org.bukkit.event.Event");

            // verify
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().data()).containsEntry("getValue", "value");
            assertRequest(
                server,
                AgentAction.GET_CAPTURED_EVENTS,
                Map.of("eventClassName", "org.bukkit.event.Event")
            );
        }
    }

    @Test
    void handshake_shouldSendHandshakeRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-handshake.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of("protocolVersion", "1", "bukkitVersion", "1.21.11")));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.handshake("my-token", 1, "sha256");

            // verify
            assertRequest(server, AgentAction.HANDSHAKE, Map.of("token", "my-token", "protocolVersion", "1", "agentSha256", "sha256"));
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
            assertRequest(server, AgentAction.MAIN_WORLD, Map.of());
        }
    }

    @Test
    void unloadChunk_shouldSendCoordinatesAndReturnResult(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-unload-chunk.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of("success", "false")));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean result = client.unloadChunk("world", 5, -3);

            // verify
            assertThat(result).isFalse();
            assertRequest(server, AgentAction.UNLOAD_CHUNK, Map.of("worldName", "world", "x", "5", "z", "-3"));
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
            assertRequest(server, AgentAction.IS_CHUNK_LOADED, Map.of("worldName", "world", "x", "1", "z", "2"));
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
            assertRequest(server, AgentAction.EXECUTE_COMMAND, Map.of("source", "CONSOLE", "command", "time set day"));
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
            assertRequest(server, AgentAction.REGISTER_EVENT_LISTENER,
                Map.of("eventClassName", "org.bukkit.event.player.PlayerJoinEvent"));
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
            assertRequest(server, AgentAction.CLEAR_CAPTURED_EVENTS,
                Map.of("eventClassName", "org.bukkit.event.player.PlayerJoinEvent"));
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
            assertRequest(server, AgentAction.UNREGISTER_EVENT_LISTENER,
                Map.of("eventClassName", "org.bukkit.event.player.PlayerJoinEvent"));
        }
    }

    @Test
    void waitTicks_shouldSendTickCount(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-wait-ticks.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, successResponse(Map.of("startTick", "0", "endTick", "5")));
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.waitTicks(5);

            // verify
            assertRequest(server, AgentAction.WAIT_TICKS, Map.of("ticks", "5"));
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
            successResponse(Map.of("platform", "Sponge"))
        ); UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final nl.pim16aap2.lightkeeper.framework.Platform result = client.serverPlatform();

            // verify
            assertThat(result).isEqualTo(nl.pim16aap2.lightkeeper.framework.Platform.UNKNOWN);
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
            final java.util.List<String> result = client.playerMessages(PLAYER_ID);

            // verify
            assertThat(result).containsExactly("hello", "world");
            assertRequest(server, AgentAction.GET_PLAYER_MESSAGES, Map.of("uuid", PLAYER_ID.toString()));
        }
    }

    private static AgentResponse successResponse(Map<String, String> data)
    {
        return new AgentResponse("1", true, null, null, data);
    }

    private static void assertRequest(AgentSocketServer server, AgentAction action, Map<String, String> arguments)
        throws IOException
    {
        final nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest request =
            REQUEST_MAPPER.readValue(server.requestLine(), nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest.class);
        assertThat(request.action()).isEqualTo(action);
        assertThat(request.arguments()).isEqualTo(arguments);
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
                final String requestLine = reader.readLine();
                if (requestLine == null)
                    return;
                this.requestLine.set(requestLine);
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
