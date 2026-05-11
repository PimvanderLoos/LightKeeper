package nl.pim16aap2.lightkeeper.framework.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import nl.pim16aap2.lightkeeper.framework.CommandSource;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import nl.pim16aap2.lightkeeper.framework.Platform;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.IAgentCommand;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.BlockTypeCommand;
import nl.pim16aap2.lightkeeper.protocol.ClickMenuSlotCommand;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEventsCommand;
import nl.pim16aap2.lightkeeper.protocol.CreatePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.DragMenuSlotsCommand;
import nl.pim16aap2.lightkeeper.protocol.DropItemCommand;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommandCommand;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommandCommand;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEventsCommand;
import nl.pim16aap2.lightkeeper.protocol.GetOpenMenuCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerChatComponentsCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerInventoryCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerMessagesCommand;
import nl.pim16aap2.lightkeeper.protocol.GetServerPlatformCommand;
import nl.pim16aap2.lightkeeper.protocol.GetServerTickCommand;
import nl.pim16aap2.lightkeeper.protocol.HandshakeCommand;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoadedCommand;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.LoadChunkCommand;
import nl.pim16aap2.lightkeeper.protocol.MainWorldCommand;
import nl.pim16aap2.lightkeeper.protocol.NewWorldCommand;
import nl.pim16aap2.lightkeeper.protocol.PlacePlayerBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListenerCommand;
import nl.pim16aap2.lightkeeper.protocol.RemovePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.RightClickBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.SetBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.TeleportPlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunkCommand;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListenerCommand;
import nl.pim16aap2.lightkeeper.protocol.WaitTicksCommand;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent RPC client backed by a Unix Domain Socket.
 */
final class UdsAgentClient implements AutoCloseable
{
    private static final System.Logger LOG = System.getLogger(UdsAgentClient.class.getName());

    // Reusable TypeReference constants. Jackson captures the generic type at runtime via
    // getGenericSuperclass(), which is preserved even when using the diamond operator on these
    // explicit-target-type field declarations (Java 9+ behaviour).
    private static final TypeReference<MenuItemSnapshot[]> TYPE_MENU_ITEM_ARRAY =
        new TypeReference<>() {};
    private static final TypeReference<String[]> TYPE_STRING_ARRAY =
        new TypeReference<>() {};
    private static final TypeReference<List<Map<String, String>>> TYPE_EVENT_DATA_LIST =
        new TypeReference<>() {};

    /**
     * Maximum time to wait for a single agent response. Matches the agent-side WAIT_TICKS_TIMEOUT_MILLIS
     * so WAIT_TICKS is the longest action that can legitimately take close to this limit.
     *
     * <p>Unix Domain Sockets do not support SO_TIMEOUT, so the timeout is enforced by a watchdog that
     * closes the channel directly; this causes {@link AsynchronousCloseException} on the blocked read.
     */
    private static final int SEND_TIMEOUT_MS = 120_000;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Single-thread watchdog executor that closes the socket channel when a read takes longer than
     * {@link #SEND_TIMEOUT_MS}. Closing the channel directly (not via {@link #close()}) avoids deadlock
     * with the {@code synchronized} monitor held by {@link #send}.
     */
    private final ScheduledExecutorService readTimeoutWatchdog = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("lk-read-timeout-watchdog").daemon(true).factory()
    );

    private final Path socketPath;
    private final AtomicLong requestCounter = new AtomicLong(0L);
    private @Nullable SocketChannel socketChannel;
    private @Nullable BufferedReader reader;
    private @Nullable BufferedWriter writer;

    UdsAgentClient(Path socketPath, Duration connectTimeout)
    {
        this.socketPath = Objects.requireNonNull(socketPath, "socketPath may not be null.");
        connect(Objects.requireNonNull(connectTimeout, "connectTimeout may not be null."));
    }

    void handshake(String token, int protocolVersion, String agentSha256)
    {
        send(new HandshakeCommand(nextRequestId(), token, protocolVersion, agentSha256));
    }

    String mainWorld()
    {
        final AgentResponse response = send(new MainWorldCommand(nextRequestId()));
        return getRequiredData(response, "worldName");
    }

    String newWorld(WorldSpec worldSpec)
    {
        final AgentResponse response = send(new NewWorldCommand(
            nextRequestId(),
            worldSpec.name(),
            worldSpec.worldType().name(),
            worldSpec.environment().name(),
            worldSpec.seed()
        ));
        return getRequiredData(response, "worldName");
    }

    boolean executeCommand(CommandSource source, String command)
    {
        final AgentResponse response = send(new ExecuteCommandCommand(nextRequestId(), source.name(), command));
        return Boolean.parseBoolean(getRequiredData(response, "success"));
    }

    String blockType(String worldName, Vector3Di position)
    {
        final AgentResponse response = send(new BlockTypeCommand(
            nextRequestId(),
            worldName,
            position.x(),
            position.y(),
            position.z()
        ));
        return getRequiredData(response, "material");
    }

    void setBlock(String worldName, Vector3Di position, String material)
    {
        send(new SetBlockCommand(
            nextRequestId(),
            worldName,
            position.x(),
            position.y(),
            position.z(),
            material
        ));
    }

    AgentPlayerData createPlayer(
        String name,
        UUID uuid,
        String worldName,
        @Nullable Double x,
        @Nullable Double y,
        @Nullable Double z,
        @Nullable Double health,
        @Nullable Set<String> permissions)
    {
        final String permissionsCsv = permissions == null || permissions.isEmpty()
            ? null
            : String.join(",", permissions);

        final AgentResponse response = send(new CreatePlayerCommand(
            nextRequestId(),
            name,
            uuid,
            worldName,
            x,
            y,
            z,
            health,
            permissionsCsv
        ));
        final UUID createdUuid = UUID.fromString(getRequiredData(response, "uuid"));
        final String createdName = getRequiredData(response, "name");
        return new AgentPlayerData(createdUuid, createdName);
    }

    void removePlayer(UUID uuid)
    {
        send(new RemovePlayerCommand(nextRequestId(), uuid));
    }

    void executePlayerCommand(UUID uuid, String command)
    {
        send(new ExecutePlayerCommandCommand(nextRequestId(), uuid, command));
    }

    void placePlayerBlock(UUID uuid, String material, int x, int y, int z)
    {
        send(new PlacePlayerBlockCommand(nextRequestId(), uuid, material, x, y, z));
    }

    void leftClickBlock(UUID uuid, Vector3Di position, String blockFace)
    {
        send(new LeftClickBlockCommand(
            nextRequestId(),
            uuid,
            position.x(),
            position.y(),
            position.z(),
            blockFace
        ));
    }

    void rightClickBlock(UUID uuid, Vector3Di position, String blockFace)
    {
        send(new RightClickBlockCommand(
            nextRequestId(),
            uuid,
            position.x(),
            position.y(),
            position.z(),
            blockFace
        ));
    }

    MenuSnapshot menuSnapshot(UUID uuid)
    {
        final AgentResponse response = send(new GetOpenMenuCommand(nextRequestId(), uuid));
        final boolean open = Boolean.parseBoolean(getRequiredData(response, "open"));
        if (!open)
            return new MenuSnapshot(false, "", List.of());

        final String title = getRequiredData(response, "title");
        final MenuItemSnapshot[] items = parseJsonField(response, "itemsJson", TYPE_MENU_ITEM_ARRAY);
        return new MenuSnapshot(true, title, List.of(items));
    }

    void clickMenuSlot(UUID uuid, int slot)
    {
        send(new ClickMenuSlotCommand(nextRequestId(), uuid, slot, "LEFT"));
    }

    void dragMenuSlots(UUID uuid, String materialKey, int... slots)
    {
        send(new DragMenuSlotsCommand(nextRequestId(), uuid, materialKey, slots));
    }

    void waitTicks(int ticks)
    {
        send(new WaitTicksCommand(nextRequestId(), ticks));
    }

    List<String> playerMessages(UUID uuid)
    {
        final AgentResponse response = send(new GetPlayerMessagesCommand(nextRequestId(), uuid));
        return List.of(parseJsonField(response, "messagesJson", TYPE_STRING_ARRAY));
    }

    List<ChatComponentSnapshot> playerChatComponents(UUID uuid)
    {
        final AgentResponse response = send(new GetPlayerChatComponentsCommand(nextRequestId(), uuid));
        return java.util.Arrays.stream(parseJsonField(response, "componentsJson", TYPE_STRING_ARRAY))
            .map(ChatComponentSnapshot::new)
            .toList();
    }

    long getServerTick()
    {
        final AgentResponse response = send(new GetServerTickCommand(nextRequestId()));
        return Long.parseLong(getRequiredData(response, "tick"));
    }

    void teleportPlayer(UUID uuid, String worldName, double x, double y, double z)
    {
        send(new TeleportPlayerCommand(nextRequestId(), uuid, worldName, x, y, z));
    }

    void loadChunk(String worldName, int x, int z)
    {
        send(new LoadChunkCommand(nextRequestId(), worldName, x, z));
    }

    boolean unloadChunk(String worldName, int x, int z)
    {
        final AgentResponse response = send(new UnloadChunkCommand(nextRequestId(), worldName, x, z));
        return Boolean.parseBoolean(getRequiredData(response, "unloaded"));
    }

    boolean isChunkLoaded(String worldName, int x, int z)
    {
        final AgentResponse response = send(new IsChunkLoadedCommand(nextRequestId(), worldName, x, z));
        return Boolean.parseBoolean(getRequiredData(response, "loaded"));
    }

    List<Map<String, Object>> getPlayerInventory(UUID uuid)
    {
        final AgentResponse response = send(new GetPlayerInventoryCommand(nextRequestId(), uuid));
        return parseJsonField(response, "inventoryJson", new TypeReference<List<Map<String, Object>>>() {});
    }

    boolean dropItem(UUID uuid)
    {
        final AgentResponse response = send(new DropItemCommand(nextRequestId(), uuid));
        return Boolean.parseBoolean(getRequiredData(response, "dropped"));
    }

    void registerEventListener(String eventClassName)
    {
        send(new RegisterEventListenerCommand(nextRequestId(), eventClassName));
    }

    List<Map<String, String>> getCapturedEvents(String eventClassName)
    {
        final AgentResponse response = send(new GetCapturedEventsCommand(nextRequestId(), eventClassName));
        return parseJsonField(response, "eventsJson", TYPE_EVENT_DATA_LIST);
    }

    void clearCapturedEvents(String eventClassName)
    {
        send(new ClearCapturedEventsCommand(nextRequestId(), eventClassName));
    }

    void unregisterEventListener(String eventClassName)
    {
        send(new UnregisterEventListenerCommand(nextRequestId(), eventClassName));
    }

    Platform serverPlatform()
    {
        final AgentResponse response = send(new GetServerPlatformCommand(nextRequestId()));
        final String platformName = getRequiredData(response, "platform").toLowerCase(java.util.Locale.ROOT);
        if (platformName.contains("paper"))
            return Platform.PAPER;
        if (platformName.contains("spigot") || platformName.contains("craftbukkit"))
            return Platform.SPIGOT;
        return Platform.UNKNOWN;
    }

    synchronized AgentResponse send(IAgentCommand command)
    {
        final BufferedWriter out = Objects.requireNonNull(writer, "Client is not connected.");
        final BufferedReader in = Objects.requireNonNull(reader, "Client is not connected.");

        // Capture the channel reference before the read; the watchdog closes it directly to avoid
        // deadlocking on this synchronized method's monitor.
        final SocketChannel channelSnapshot = this.socketChannel;
        final ScheduledFuture<?> watchdog = readTimeoutWatchdog.schedule(() ->
        {
            if (channelSnapshot != null)
            {
                try
                {
                    channelSnapshot.close();
                }
                catch (IOException ignored)
                {
                    // Best-effort: the channel may already be closed.
                }
            }
        }, SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        try
        {
            out.write(objectMapper.writeValueAsString(command));
            out.newLine();
            out.flush();

            final String responseLine = in.readLine();
            if (responseLine == null)
                throw new IllegalStateException("Agent connection closed unexpectedly.");

            final String requestId = command.requestId();
            final AgentResponse response = objectMapper.readValue(responseLine, AgentResponse.class);
            if (!requestId.equals(response.requestId()))
            {
                throw new IllegalStateException(
                    "Unexpected response id '%s' for request '%s'."
                        .formatted(response.requestId(), requestId)
                );
            }

            if (!response.success())
            {
                final AgentErrorCode errorCode = AgentErrorCode.fromWireCode(response.errorCode())
                    .orElse(AgentErrorCode.UNKNOWN);
                final String expectedProtocolVersion = response.data().get("expectedProtocolVersion");
                final String actualProtocolVersion = response.data().get("actualProtocolVersion");
                final String protocolDetail = expectedProtocolVersion != null || actualProtocolVersion != null
                    ? " expectedProtocolVersion=%s actualProtocolVersion=%s"
                      .formatted(expectedProtocolVersion, actualProtocolVersion)
                    : "";
                throw new IllegalStateException(
                    "Agent request failed. code=%s message=%s%s"
                        .formatted(errorCode.wireCode(), response.errorMessage(), protocolDetail)
                );
            }

            return response;
        }
        catch (AsynchronousCloseException exception)
        {
            throw new IllegalStateException(
                "Agent did not respond within %d ms for action '%s' via socket '%s'."
                    .formatted(SEND_TIMEOUT_MS, command.getClass().getSimpleName(), socketPath),
                exception
            );
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                "Failed to communicate with agent via socket '%s'.".formatted(socketPath),
                exception
            );
        }
        finally
        {
            watchdog.cancel(false);
        }
    }

    synchronized void rehandshake(Duration timeout, String token, int protocolVersion, String agentSha256)
    {
        close();
        connect(timeout);
        handshake(token, protocolVersion, agentSha256);
    }

    @Override
    public synchronized void close()
    {
        try
        {
            if (socketChannel != null)
            {
                socketChannel.close();
                socketChannel = null;
            }
        }
        catch (IOException ignored)
        {
            LOG.log(System.Logger.Level.TRACE, "Failed to close agent socket channel cleanly.");
        }
        finally
        {
            reader = null;
            writer = null;
        }
    }

    private void connect(Duration timeout)
    {
        final long deadline = System.nanoTime() + timeout.toNanos();
        Exception lastException = null;

        while (System.nanoTime() < deadline)
        {
            SocketChannel channel = null;
            try
            {
                channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                channel.connect(UnixDomainSocketAddress.of(socketPath));
                this.socketChannel = channel;
                this.reader = new BufferedReader(
                    new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8)
                );
                this.writer = new BufferedWriter(
                    new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8)
                );
                return;
            }
            catch (Exception exception)
            {
                lastException = exception;
                closeFailedChannel(channel, exception);
                sleep(100L);
            }
        }

        throw new IllegalStateException(
            "Failed to connect to agent socket '%s' within timeout %s."
                .formatted(socketPath, timeout),
            lastException
        );
    }

    private static void closeFailedChannel(@Nullable SocketChannel channel, Exception connectionException)
    {
        if (channel == null)
            return;
        try
        {
            channel.close();
        }
        catch (IOException closeException)
        {
            connectionException.addSuppressed(closeException);
        }
    }

    private static String getRequiredData(AgentResponse response, String key)
    {
        final String value = response.data().get(key);
        if (value == null)
            throw new IllegalStateException("Missing response field '%s' from agent.".formatted(key));
        return value;
    }

    /**
     * Reads a JSON string from a response data field and deserializes it using the supplied type reference.
     *
     * @param response
     *     Agent response whose data map is searched.
     * @param key
     *     Data field key; defaults to {@code "[]"} when absent.
     * @param typeRef
     *     Jackson type reference for deserialization.
     * @param <T>
     *     Expected return type.
     * @return
     *     Deserialized value.
     * @throws IllegalStateException
     *     When deserialization fails.
     */
    private <T> T parseJsonField(AgentResponse response, String key, TypeReference<T> typeRef)
    {
        final String json = response.data().getOrDefault(key, "[]");
        try
        {
            return objectMapper.readValue(json, typeRef);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Failed to parse '%s' JSON from agent response.".formatted(key), exception);
        }
    }

    private static void sleep(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for agent connection.", exception);
        }
    }

    private String nextRequestId()
    {
        return Long.toString(requestCounter.incrementAndGet());
    }
}
