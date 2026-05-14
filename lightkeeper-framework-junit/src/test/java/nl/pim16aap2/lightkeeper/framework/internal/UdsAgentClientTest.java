package nl.pim16aap2.lightkeeper.framework.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.WaitTicks;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void send_shouldThrowExceptionWhenResponseIdDoesNotMatch(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-mismatch.sock");
        final String responseJson = "{\"requestId\":\"unexpected-request-id\",\"success\":true,"
            + "\"startTick\":0,\"endTick\":0}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute + verify
            assertThatThrownBy(() -> client.send(new WaitTicks.Command("1", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected response id");
            client.close();
        }
    }

    @Test
    void send_shouldThrowWhenAgentReportsFailure(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-failure.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":false,"
            + "\"errorCode\":\"PROTOCOL_MISMATCH\",\"errorMessage\":\"Protocol mismatch.\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute + verify
            assertThatThrownBy(() -> client.send(new WaitTicks.Command("1", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("code=PROTOCOL_MISMATCH");
            client.close();
        }
    }

    @Test
    void mainWorld_shouldReturnWorldNameFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("main-world.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"worldName\":\"world\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final String worldName = client.mainWorld();

            // verify
            assertThat(worldName).isEqualTo("world");
            assertThat(server.capturedRequest()).contains("\"action\":\"MAIN_WORLD\"");
        }
    }

    @Test
    void getServerTick_shouldReturnTickValueFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("server-tick.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"tick\":42}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final long tick = client.getServerTick();

            // verify
            assertThat(tick).isEqualTo(42L);
            assertThat(server.capturedRequest()).contains("\"action\":\"GET_SERVER_TICK\"");
        }
    }

    @Test
    void isChunkLoaded_shouldReturnTrueWhenResponseIndicatesLoaded(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("chunk-loaded.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"loaded\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean loaded = client.isChunkLoaded("world", 0, 0);

            // verify
            assertThat(loaded).isTrue();
            assertThat(server.capturedRequest()).contains("\"action\":\"IS_CHUNK_LOADED\"");
        }
    }

    @Test
    void dropItem_shouldReturnTrueWhenEventWasNotCancelled(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("drop-item.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"eventCancelled\":false}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean dropped = client.dropItem(UUID.randomUUID());

            // verify — dropItem() returns !eventCancelled
            assertThat(dropped).isTrue();
        }
    }

    @Test
    void dropItem_shouldReturnFalseWhenEventWasCancelled(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("drop-item-cancel.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"eventCancelled\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean dropped = client.dropItem(UUID.randomUUID());

            // verify
            assertThat(dropped).isFalse();
        }
    }

    @Test
    void getPlayerInventory_shouldParseInventoryJsonFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("inventory.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,\"inventoryJson\":\"[{\\\"slot\\\":0,\\\"materialKey\\\":\\\"minecraft:stone\\\"}]\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final List<Map<String, Object>> inventory = client.getPlayerInventory(UUID.randomUUID());

            // verify
            assertThat(inventory).hasSize(1);
            assertThat(inventory.getFirst()).containsEntry("materialKey", "minecraft:stone");
        }
    }

    @Test
    void getCapturedEvents_shouldParseEventsJsonFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("events.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,\"eventsJson\":\"[{\\\"player\\\":\\\"Steve\\\"}]\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final List<Map<String, String>> events = client.getCapturedEvents("org.bukkit.event.player.PlayerJoinEvent");

            // verify
            assertThat(events).hasSize(1);
            assertThat(events.getFirst()).containsEntry("player", "Steve");
        }
    }

    @Test
    void serverPlatform_shouldReturnServerNameFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("platform.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,\"serverName\":\"Paper\",\"serverVersion\":\"1.21.11\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final String platform = client.serverPlatform();

            // verify
            assertThat(platform).isEqualTo("Paper");
            assertThat(server.capturedRequest()).contains("\"action\":\"GET_SERVER_PLATFORM\"");
        }
    }

    @Test
    void executeCommand_shouldSendConsoleSourceInRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("exec-cmd.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"success\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.executeCommand(CommandSource.CONSOLE, "time set day");

            // verify
            assertThat(server.capturedRequest())
                .contains("\"action\":\"EXECUTE_COMMAND\"")
                .contains("\"commandSource\":\"CONSOLE\"");
        }
    }

    private static final class AgentSocketServer implements AutoCloseable
    {
        private final ServerSocketChannel serverChannel;
        private final Thread workerThread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        private final AtomicReference<String> requestLine = new AtomicReference<>("");
        private final Path socketPath;

        private AgentSocketServer(Path socketPath, String responseJson)
            throws IOException
        {
            this.socketPath = socketPath;
            this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
            this.workerThread = new Thread(() -> serveSingleResponse(responseJson), "uds-agent-client-test-server");
            started.countDown();
            this.workerThread.start();
        }

        private static AgentSocketServer start(Path socketPath, String responseJson)
            throws IOException, InterruptedException
        {
            final AgentSocketServer server = new AgentSocketServer(socketPath, responseJson);
            if (!server.started.await(3, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out while waiting for test socket server startup.");
            return server;
        }

        String capturedRequest()
        {
            return requestLine.get();
        }

        private void serveSingleResponse(String responseJson)
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
                requestLine.set(line);
                writer.write(responseJson);
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
    }
}
