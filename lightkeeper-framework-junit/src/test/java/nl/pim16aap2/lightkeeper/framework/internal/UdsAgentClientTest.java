package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import nl.pim16aap2.lightkeeper.framework.Platform;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.DropResult;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import nl.pim16aap2.lightkeeper.protocol.ItemSnapshot;
import nl.pim16aap2.lightkeeper.protocol.MutatePlayerPermission;
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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class UdsAgentClientTest
{
    private static final UUID PLAYER_ID =
        UUID.fromString("6efa93e0-6b5f-45b7-8af8-2453c9c7ef0c");

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
    void send_shouldPreserveUnknownWireErrorCode(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-unknown-error.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":false,"
            + "\"errorCode\":\"FUTURE_CODE\",\"errorMessage\":\"Future failure.\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute
            final Throwable throwable = catchThrowable(() -> client.send(new WaitTicks.Command("1", 1)));

            // verify
            assertThat(throwable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("code=UNKNOWN (wire='FUTURE_CODE')");
            client.close();
        }
    }

    @Test
    void send_shouldSurfaceServerErrorWhenResponseIsUncorrelated(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup — a parse failure replies with the "unknown" id and success=false, as the server does
        final Path socketPath = tempDirectory.resolve("agent-parse-error.sock");
        final String responseJson = "{\"requestId\":\"unknown\",\"success\":false,"
            + "\"errorCode\":\"INVALID_REQUEST\",\"errorMessage\":\"Failed to parse request: boom\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute + verify — the id mismatch must not mask the server's real error code and message
            assertThatThrownBy(() -> client.send(new WaitTicks.Command("42", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVALID_REQUEST")
                .hasMessageContaining("Failed to parse request");
            client.close();
        }
    }

    @Test
    void send_shouldReportTimeoutWhenAgentNeverResponds(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-silent.sock");
        try (AgentSocketServer server = AgentSocketServer.startSilent(socketPath))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3), 200L);

            // execute + verify — the watchdog closes the channel and the timeout surfaces with the wait bound
            assertThatThrownBy(() -> client.send(new WaitTicks.Command("1", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not respond within 200 ms");
            client.close();
        }
    }

    @Test
    void send_shouldFailFastAfterTimeout(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-silent-then-broken.sock");
        try (AgentSocketServer server = AgentSocketServer.startSilent(socketPath))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3), 200L);
            assertThatThrownBy(() -> client.send(new WaitTicks.Command("1", 1)))
                .isInstanceOf(IllegalStateException.class);

            // execute + verify — the client is closed after the timeout; the next send fails fast, referencing it
            assertThatThrownBy(() -> client.send(new WaitTicks.Command("2", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client is not connected")
                .hasMessageContaining("timed out");
            client.close();
        }
    }

    @Test
    void send_shouldSucceedForSecondRequestAfterSlowButInTimeResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup — responses arrive after 100 ms, comfortably within the 1000 ms client timeout
        final Path socketPath = tempDirectory.resolve("agent-slow.sock");
        try (AgentSocketServer server = AgentSocketServer.startEchoing(socketPath, 100L, 2))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3), 1_000L);

            // execute
            final WaitTicks.Response first = client.send(new WaitTicks.Command("1", 1));
            final WaitTicks.Response second = client.send(new WaitTicks.Command("2", 1));

            // verify — the watchdog is cancelled on the in-time first response, so the second request also succeeds
            assertThat(first.startTick()).isZero();
            assertThat(first.endTick()).isZero();
            assertThat(second.startTick()).isZero();
            assertThat(second.endTick()).isZero();
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
    void dropItem_shouldReturnDroppedWhenEventWasNotCancelled(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("drop-item.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"result\":\"DROPPED\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final DropResult result = client.dropItem(UUID.randomUUID());

            // verify
            assertThat(result).isEqualTo(DropResult.DROPPED);
        }
    }

    @Test
    void dropItem_shouldReturnCancelledWhenEventWasCancelled(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("drop-item-cancel.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"result\":\"CANCELLED\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final DropResult result = client.dropItem(UUID.randomUUID());

            // verify
            assertThat(result).isEqualTo(DropResult.CANCELLED);
        }
    }

    @Test
    void dropItem_shouldReturnEmptyHandWhenMainHandIsEmpty(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("drop-item-empty-hand.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"result\":\"EMPTY_HAND\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final DropResult result = client.dropItem(UUID.randomUUID());

            // verify
            assertThat(result).isEqualTo(DropResult.EMPTY_HAND);
        }
    }

    @Test
    void leftClickBlock_shouldReturnCancelledFlagFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("left-click-block.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"cancelled\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean cancelled = client.leftClickBlock(PLAYER_ID, new BlockPos(1, 64, 1), "UP");

            // verify
            assertThat(cancelled).isTrue();
            assertThat(server.capturedRequest()).contains("\"action\":\"LEFT_CLICK_BLOCK\"");
        }
    }

    @Test
    void rightClickBlock_shouldReturnCancelledFlagFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("right-click-block.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"cancelled\":false}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean cancelled = client.rightClickBlock(PLAYER_ID, new BlockPos(1, 64, 1), "UP");

            // verify
            assertThat(cancelled).isFalse();
            assertThat(server.capturedRequest()).contains("\"action\":\"RIGHT_CLICK_BLOCK\"");
        }
    }

    @Test
    void getPlayerInventory_shouldReturnTypedItemsFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup — items are carried as a typed array on the envelope, not a re-escaped JSON string
        final Path socketPath = tempDirectory.resolve("inventory.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,"
                + "\"items\":[{\"slot\":0,\"materialKey\":\"minecraft:stone\",\"lore\":[]}]}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final List<ItemSnapshot> inventory = client.getPlayerInventory(UUID.randomUUID());

            // verify
            assertThat(inventory).singleElement().satisfies(item ->
            {
                assertThat(item.slot()).isEqualTo(0);
                assertThat(item.materialKey()).isEqualTo("minecraft:stone");
            });
        }
    }

    @Test
    void getCapturedEvents_shouldReturnEventsFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("events.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,"
                + "\"events\":[{\"tick\":7,\"values\":{\"player\":{\"type\":\"STRING\",\"value\":\"Steve\"}}}]}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final List<GetCapturedEvents.CapturedEvent> events =
                client.getCapturedEvents("org.bukkit.event.player.PlayerJoinEvent");

            // verify
            assertThat(events).hasSize(1);
            assertThat(events.getFirst().tick()).isEqualTo(7L);
            assertThat(events.getFirst().values()).containsEntry("player", new IProtocolValue.PString("Steve"));
        }
    }

    @Test
    void cancelNextEvents_shouldSendCancelRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("cancel-next-events.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.cancelNextEvents("org.bukkit.event.player.PlayerJoinEvent", 3);

            // verify
            assertThat(server.capturedRequest())
                .contains("\"action\":\"CANCEL_NEXT_EVENTS\"")
                .contains("PlayerJoinEvent")
                .contains("\"count\":3");
        }
    }

    @Test
    void playerChat_shouldSendChatRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("player-chat.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.playerChat(PLAYER_ID, "hello world");

            // verify
            assertThat(server.capturedRequest())
                .contains("\"action\":\"PLAYER_CHAT\"")
                .contains("\"message\":\"hello world\"");
        }
    }

    @Test
    void getServerErrors_shouldReturnTypedEntriesFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("server-errors.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,\"droppedCount\":2,\"captureActive\":true,\"errors\":["
                + "{\"timestampMillis\":123,\"severity\":\"ERROR\",\"levelName\":\"ERROR\","
                + "\"loggerName\":\"net.example\",\"threadName\":\"Server thread\",\"message\":\"boom\","
                + "\"throwableClass\":\"java.lang.IllegalStateException\",\"throwableMessage\":\"boom\","
                + "\"stackTrace\":[\"java.lang.IllegalStateException: boom\"]}]}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final nl.pim16aap2.lightkeeper.protocol.GetServerErrors.Response response = client.getServerErrors();

            // verify
            assertThat(server.capturedRequest()).contains("\"action\":\"GET_SERVER_ERRORS\"");
            assertThat(response.droppedCount()).isEqualTo(2L);
            assertThat(response.captureActive()).isTrue();
            assertThat(response.errors()).singleElement().satisfies(entry ->
            {
                assertThat(entry.severity()).isEqualTo("ERROR");
                assertThat(entry.message()).isEqualTo("boom");
                assertThat(entry.throwableClass()).isEqualTo("java.lang.IllegalStateException");
                assertThat(entry.stackTrace()).hasSize(1);
            });
        }
    }

    @Test
    void clearServerErrors_shouldSendClearRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("clear-server-errors.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.clearServerErrors();

            // verify
            assertThat(server.capturedRequest()).contains("\"action\":\"CLEAR_SERVER_ERRORS\"");
        }
    }

    @Test
    void serverPlatform_shouldMapPaperResponseToPaper(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("platform.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,\"platform\":\"PAPER\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final Platform platform = client.serverPlatform();

            // verify
            assertThat(platform).isEqualTo(Platform.PAPER);
            assertThat(server.capturedRequest()).contains("\"action\":\"GET_SERVER_PLATFORM\"");
        }
    }

    @Test
    void serverPlatform_shouldMapUnrecognizedResponseToUnknown(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("platform-unknown.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,\"platform\":\"FOLIA\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final Platform platform = client.serverPlatform();

            // verify
            assertThat(platform).isEqualTo(Platform.UNKNOWN);
            assertThat(server.capturedRequest()).contains("\"action\":\"GET_SERVER_PLATFORM\"");
        }
    }

    @Test
    void executeCommand_shouldSendConsoleSourceInRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("exec-cmd.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"dispatched\":true}";
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

    @Test
    void menuSnapshot_shouldReturnTypedItemsForOpenMenu(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup — items are carried as a typed array on the envelope, not a re-escaped JSON string
        final Path socketPath = tempDirectory.resolve("menu-open.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,\"open\":true,\"title\":\"Chest\","
                + "\"items\":[{\"slot\":3,\"materialKey\":\"minecraft:stone\",\"displayName\":\"Rock\","
                + "\"lore\":[\"line\"]}]}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final var snapshot = client.menuSnapshot(UUID.randomUUID());

            // verify
            assertThat(snapshot.open()).isTrue();
            assertThat(snapshot.title()).isEqualTo("Chest");
            assertThat(snapshot.items()).singleElement().satisfies(item ->
            {
                assertThat(item.slot()).isEqualTo(3);
                assertThat(item.materialKey()).isEqualTo("minecraft:stone");
                assertThat(item.displayName()).isEqualTo("Rock");
                assertThat(item.lore()).containsExactly("line");
            });
        }
    }

    @Test
    void teleportPlayer_shouldSendTeleportRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("teleport.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"teleported\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean teleported = client.teleportPlayer(UUID.randomUUID(), "world", 1.0, 64.0, 1.0);

            // verify
            assertThat(teleported).isTrue();
            assertThat(server.capturedRequest()).contains("\"action\":\"TELEPORT_PLAYER\"");
        }
    }

    @Test
    void loadChunk_shouldSendLoadChunkRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("load-chunk.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"loaded\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean loaded = client.loadChunk("world", 0, 0);

            // verify
            assertThat(loaded).isTrue();
            assertThat(server.capturedRequest()).contains("\"action\":\"LOAD_CHUNK\"");
        }
    }

    @Test
    void unloadChunk_shouldSendUnloadChunkRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("unload-chunk.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"unloaded\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean unloaded = client.unloadChunk("world", 0, 0);

            // verify
            assertThat(unloaded).isTrue();
            assertThat(server.capturedRequest()).contains("\"action\":\"UNLOAD_CHUNK\"");
        }
    }

    @Test
    void registerEventListener_shouldSendRegisterRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("register-event.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.registerEventListener("org.bukkit.event.player.PlayerJoinEvent");

            // verify
            assertThat(server.capturedRequest())
                .contains("\"action\":\"REGISTER_EVENT_LISTENER\"")
                .contains("PlayerJoinEvent");
        }
    }

    @Test
    void clearCapturedEvents_shouldSendClearRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("clear-events.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.clearCapturedEvents("org.bukkit.event.player.PlayerJoinEvent");

            // verify
            assertThat(server.capturedRequest())
                .contains("\"action\":\"CLEAR_CAPTURED_EVENTS\"")
                .contains("PlayerJoinEvent");
        }
    }

    @Test
    void unregisterEventListener_shouldSendUnregisterRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("unregister-event.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.unregisterEventListener("org.bukkit.event.player.PlayerJoinEvent");

            // verify
            assertThat(server.capturedRequest())
                .contains("\"action\":\"UNREGISTER_EVENT_LISTENER\"")
                .contains("PlayerJoinEvent");
        }
    }

    @Test
    void send_shouldThrowWithVersionInfoWhenProtocolMismatch(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("mismatch.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":false,"
            + "\"errorCode\":\"PROTOCOL_MISMATCH\","
            + "\"errorMessage\":\"Runtime protocol version mismatch. expected=7 actual=8.\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute + verify
            assertThatThrownBy(() -> client.send(new WaitTicks.Command("1", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROTOCOL_MISMATCH")
                .hasMessageContaining("expected=7")
                .hasMessageContaining("actual=8");
            client.close();
        }
    }

    @Test
    void playerChatComponents_shouldParseComponentSnapshots(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-components.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,"
                + "\"componentsJson\":\"[\\\"{\\\\\\\"text\\\\\\\":\\\\\\\"Hi\\\\\\\"}\\\"]\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final List<ChatComponentSnapshot> result = client.playerChatComponents(PLAYER_ID);

            // verify
            assertThat(result).extracting(ChatComponentSnapshot::json)
                .containsExactly("{\"text\":\"Hi\"}");
            assertThat(server.capturedRequest()).contains("\"action\":\"GET_PLAYER_CHAT_COMPONENTS\"");
        }
    }

    @Test
    void mutatePlayerPermission_shouldSendGrantModeInRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("mutate-permission-grant.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.mutatePlayerPermission(PLAYER_ID, "lightkeeper.fly", MutatePlayerPermission.Mode.GRANT);

            // verify
            assertThat(server.capturedRequest())
                .contains("\"action\":\"MUTATE_PLAYER_PERMISSION\"")
                .contains("\"permission\":\"lightkeeper.fly\"")
                .contains("\"mode\":\"GRANT\"");
        }
    }

    @Test
    void mutatePlayerPermission_shouldSendRevokeModeInRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("mutate-permission-revoke.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.mutatePlayerPermission(PLAYER_ID, "lightkeeper.fly", MutatePlayerPermission.Mode.REVOKE);

            // verify
            assertThat(server.capturedRequest())
                .contains("\"action\":\"MUTATE_PLAYER_PERMISSION\"")
                .contains("\"permission\":\"lightkeeper.fly\"")
                .contains("\"mode\":\"REVOKE\"");
        }
    }

    @Test
    void mutatePlayerPermission_shouldSendUnsetModeInRequest(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("mutate-permission-unset.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.mutatePlayerPermission(PLAYER_ID, "lightkeeper.fly", MutatePlayerPermission.Mode.UNSET);

            // verify
            assertThat(server.capturedRequest())
                .contains("\"action\":\"MUTATE_PLAYER_PERMISSION\"")
                .contains("\"permission\":\"lightkeeper.fly\"")
                .contains("\"mode\":\"UNSET\"");
        }
    }

    @Test
    void hasPlayerPermission_shouldReturnTrueFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("has-permission-true.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"value\":true}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean result = client.hasPlayerPermission(PLAYER_ID, "lightkeeper.fly");

            // verify
            assertThat(result).isTrue();
            assertThat(server.capturedRequest())
                .contains("\"action\":\"HAS_PLAYER_PERMISSION\"")
                .contains("\"permission\":\"lightkeeper.fly\"");
        }
    }

    @Test
    void setBlockData_shouldSendSetBlockRequestWithBlockDataAndDerivedMaterialKey(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("set-block-data.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"material\":\"LEVER\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            client.setBlockData("world", new BlockPos(1, 64, 1), "minecraft:lever[powered=true]");

            // verify
            assertThat(server.capturedRequest())
                .contains("\"action\":\"SET_BLOCK\"")
                .contains("\"materialKey\":\"minecraft:lever\"")
                .contains("\"blockData\":\"minecraft:lever[powered=true]\"");
        }
    }

    @Test
    void blockData_shouldReturnBlockDataFieldFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("block-data.sock");
        final String responseJson =
            "{\"requestId\":\"1\",\"success\":true,\"material\":\"minecraft:lever\","
                + "\"blockData\":\"minecraft:lever[face=floor,facing=north,powered=true]\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final String blockData = client.blockData("world", new BlockPos(1, 64, 1));

            // verify
            assertThat(blockData).isEqualTo("minecraft:lever[face=floor,facing=north,powered=true]");
            assertThat(server.capturedRequest()).contains("\"action\":\"BLOCK_TYPE\"");
        }
    }

    @Test
    void queryEntities_shouldSendUnboundedRequestWithZeroedBoundsWhenBoundsAreNull(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("query-entities-unbounded.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"tick\":10,\"count\":0,\"entities\":[]}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final var response = client.queryEntities("world", null, null, null, false);

            // verify
            assertThat(response.tick()).isEqualTo(10L);
            assertThat(response.count()).isZero();
            assertThat(server.capturedRequest())
                .contains("\"action\":\"QUERY_ENTITIES\"")
                .contains("\"worldName\":\"world\"")
                .contains("\"bounded\":false")
                .contains("\"minX\":0")
                .contains("\"maxZ\":0")
                .contains("\"countOnly\":false");
        }
    }

    @Test
    void queryEntities_shouldSendBoundedRequestOnlyWhenBothBoundsArePresent(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("query-entities-bounded.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"tick\":1,\"count\":2,\"entities\":[]}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final var response = client.queryEntities(
                "world", "minecraft:zombie", new BlockPos(0, 1, 2), new BlockPos(10, 11, 12), false);

            // verify
            assertThat(response.count()).isEqualTo(2);
            assertThat(server.capturedRequest())
                .contains("\"action\":\"QUERY_ENTITIES\"")
                .contains("\"entityTypeKey\":\"minecraft:zombie\"")
                .contains("\"bounded\":true")
                .contains("\"minX\":0")
                .contains("\"minY\":1")
                .contains("\"minZ\":2")
                .contains("\"maxX\":10")
                .contains("\"maxY\":11")
                .contains("\"maxZ\":12");
        }
    }

    @Test
    void queryEntities_shouldRejectMixedBounds(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup — a lone bound is a caller bug and must fail fast instead of silently querying unbounded
        final Path socketPath = tempDirectory.resolve("query-entities-partial-bounds.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"tick\":1,\"count\":0,\"entities\":[]}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final Throwable thrown =
                catchThrowable(() -> client.queryEntities("world", null, new BlockPos(0, 1, 2), null, false));

            // verify
            assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both present or both absent");
        }
    }

    @Test
    void queryEntities_shouldSendCountOnlyFlag(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("query-entities-count-only.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"tick\":1,\"count\":5,\"entities\":[]}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final var response = client.queryEntities("world", null, null, null, true);

            // verify
            assertThat(response.count()).isEqualTo(5);
            assertThat(server.capturedRequest()).contains("\"countOnly\":true");
        }
    }

    @Test
    void hasPlayerPermission_shouldReturnFalseFromResponse(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("has-permission-false.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":true,\"value\":false}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson);
             UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3)))
        {
            // execute
            final boolean result = client.hasPlayerPermission(PLAYER_ID, "lightkeeper.fly");

            // verify
            assertThat(result).isFalse();
        }
    }

    private static final class AgentSocketServer implements AutoCloseable
    {
        private enum Mode
        {
            /** Answer once with a fixed canned response. */
            FIXED,
            /** Answer each request with a success response echoing the request's own {@code requestId}. */
            ECHO,
            /** Accept and read but never respond, holding the connection so the client's watchdog fires. */
            HOLD
        }

        private static final long HOLD_MILLIS = 1_000L;
        private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("\"requestId\"\\s*:\\s*\"([^\"]+)\"");

        private final ServerSocketChannel serverChannel;
        private final Thread workerThread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        private final AtomicReference<String> requestLine = new AtomicReference<>("");
        private final Path socketPath;

        private AgentSocketServer(
            Path socketPath, String responseJson, long responseDelayMillis, int maxRequests, Mode mode)
            throws IOException
        {
            this.socketPath = socketPath;
            this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
            this.workerThread = new Thread(
                () -> serve(responseJson, responseDelayMillis, maxRequests, mode), "uds-agent-client-test-server");
            this.workerThread.setDaemon(true);
            started.countDown();
            this.workerThread.start();
        }

        private static AgentSocketServer start(Path socketPath, String responseJson)
            throws IOException, InterruptedException
        {
            return await(new AgentSocketServer(socketPath, responseJson, 0L, 1, Mode.FIXED));
        }

        private static AgentSocketServer startSilent(Path socketPath)
            throws IOException, InterruptedException
        {
            return await(new AgentSocketServer(socketPath, "", 0L, 1, Mode.HOLD));
        }

        private static AgentSocketServer startEchoing(Path socketPath, long responseDelayMillis, int maxRequests)
            throws IOException, InterruptedException
        {
            return await(new AgentSocketServer(socketPath, "", responseDelayMillis, maxRequests, Mode.ECHO));
        }

        private static AgentSocketServer await(AgentSocketServer server)
            throws InterruptedException
        {
            if (!server.started.await(3, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out while waiting for test socket server startup.");
            return server;
        }

        String capturedRequest()
        {
            return Objects.requireNonNullElse(requestLine.get(), "");
        }

        private void serve(String responseJson, long responseDelayMillis, int maxRequests, Mode mode)
        {
            try (SocketChannel clientChannel = serverChannel.accept();
                 BufferedReader reader = new BufferedReader(
                     Channels.newReader(clientChannel, StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(
                     Channels.newWriter(clientChannel, StandardCharsets.UTF_8)))
            {
                for (int handled = 0; handled < maxRequests; handled++)
                {
                    final String line = reader.readLine();
                    if (line == null)
                        return;
                    requestLine.set(line);

                    if (mode == Mode.HOLD)
                    {
                        Thread.sleep(HOLD_MILLIS);
                        return;
                    }
                    if (responseDelayMillis > 0)
                        Thread.sleep(responseDelayMillis);

                    final String response = mode == Mode.ECHO
                        ? "{\"requestId\":\"%s\",\"success\":true,\"startTick\":0,\"endTick\":0}"
                            .formatted(extractRequestId(line))
                        : responseJson;
                    writer.write(response);
                    writer.newLine();
                    writer.flush();
                }
            }
            catch (Throwable throwable)
            {
                workerFailure.set(throwable);
            }
        }

        private static String extractRequestId(String line)
        {
            final Matcher matcher = REQUEST_ID_PATTERN.matcher(line);
            return matcher.find() ? matcher.group(1) : "unknown";
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
