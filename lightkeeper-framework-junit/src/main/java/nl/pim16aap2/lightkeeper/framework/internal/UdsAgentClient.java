package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import nl.pim16aap2.lightkeeper.framework.Platform;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.BlockType;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.ClickMenuSlot;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.CreatePlayer;
import nl.pim16aap2.lightkeeper.protocol.DragMenuSlots;
import nl.pim16aap2.lightkeeper.protocol.DropItem;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommand;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.GetOpenMenu;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerChatComponents;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerInventory;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerMessages;
import nl.pim16aap2.lightkeeper.protocol.GetServerPlatform;
import nl.pim16aap2.lightkeeper.protocol.GetServerTick;
import nl.pim16aap2.lightkeeper.protocol.Handshake;
import nl.pim16aap2.lightkeeper.protocol.IAgentCommand;
import nl.pim16aap2.lightkeeper.protocol.IAgentResponse;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoaded;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlock;
import nl.pim16aap2.lightkeeper.protocol.LoadChunk;
import nl.pim16aap2.lightkeeper.protocol.MainWorld;
import nl.pim16aap2.lightkeeper.protocol.NewWorld;
import nl.pim16aap2.lightkeeper.protocol.PlacePlayerBlock;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListener;
import nl.pim16aap2.lightkeeper.protocol.RemovePlayer;
import nl.pim16aap2.lightkeeper.protocol.RightClickBlock;
import nl.pim16aap2.lightkeeper.protocol.SetBlock;
import nl.pim16aap2.lightkeeper.protocol.TeleportPlayer;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunk;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListener;
import nl.pim16aap2.lightkeeper.protocol.WaitTicks;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
 *
 * <p>Each public method serializes a typed {@link IAgentCommand} to JSON, writes it to the socket, reads back
 * a single-line JSON response, verifies success, and deserializes the typed {@link IAgentResponse} via
 * {@link IAgentCommand#responseType()}.
 */
final class UdsAgentClient implements AutoCloseable
{
    private static final System.Logger LOG = System.getLogger(UdsAgentClient.class.getName());

    private static final TypeReference<List<Map<String, String>>> TYPE_EVENT_DATA_LIST =
        new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> TYPE_INVENTORY_ITEM_LIST =
        new TypeReference<>() {};

    /**
     * Maximum time to wait for a single agent response. Matches the agent-side WAIT_TICKS_TIMEOUT_MILLIS
     * so WAIT_TICKS is the longest action that can legitimately take close to this limit.
     *
     * <p>Unix Domain Sockets do not support SO_TIMEOUT, so the timeout is enforced by a watchdog that
     * closes the channel directly; this causes {@link AsynchronousCloseException} on the blocked read.
     */
    private static final int SEND_TIMEOUT_MS = 120_000;

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

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
        final Handshake.Command command = new Handshake.Command(nextRequestId(), token, protocolVersion, agentSha256);
        send(command);
    }

    String mainWorld()
    {
        final MainWorld.Command command = new MainWorld.Command(nextRequestId());
        return send(command).worldName();
    }

    String newWorld(WorldSpec worldSpec)
    {
        final NewWorld.Command command = new NewWorld.Command(
            nextRequestId(),
            worldSpec.name(),
            worldSpec.worldType().name(),
            worldSpec.environment().name(),
            worldSpec.seed()
        );
        return send(command).worldName();
    }

    boolean executeCommand(CommandSource source, String command)
    {
        final ExecuteCommand.Command cmd = new ExecuteCommand.Command(nextRequestId(), source, command);
        return send(cmd).dispatched();
    }

    String blockType(String worldName, Vector3Di position)
    {
        final BlockType.Command command = new BlockType.Command(
            nextRequestId(),
            worldName,
            position.x(),
            position.y(),
            position.z()
        );
        return send(command).material();
    }

    void setBlock(String worldName, Vector3Di position, String material)
    {
        final SetBlock.Command command = new SetBlock.Command(
            nextRequestId(),
            worldName,
            position.x(),
            position.y(),
            position.z(),
            material
        );
        send(command);
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

        final CreatePlayer.Command command = new CreatePlayer.Command(
            nextRequestId(),
            name,
            uuid,
            worldName,
            x,
            y,
            z,
            health,
            permissionsCsv
        );
        final CreatePlayer.Response response = send(command);
        return new AgentPlayerData(response.uuid(), response.name());
    }

    void removePlayer(UUID uuid)
    {
        final RemovePlayer.Command command = new RemovePlayer.Command(nextRequestId(), uuid);
        send(command);
    }

    void executePlayerCommand(UUID uuid, String command)
    {
        final ExecutePlayerCommand.Command cmd = new ExecutePlayerCommand.Command(nextRequestId(), uuid, command);
        send(cmd);
    }

    void placePlayerBlock(UUID uuid, String material, int x, int y, int z)
    {
        final PlacePlayerBlock.Command command = new PlacePlayerBlock.Command(nextRequestId(), uuid, material, x, y, z);
        send(command);
    }

    void leftClickBlock(UUID uuid, Vector3Di position, String blockFace)
    {
        final LeftClickBlock.Command command = new LeftClickBlock.Command(
            nextRequestId(),
            uuid,
            position.x(),
            position.y(),
            position.z(),
            blockFace
        );
        send(command);
    }

    void rightClickBlock(UUID uuid, Vector3Di position, String blockFace)
    {
        final RightClickBlock.Command command = new RightClickBlock.Command(
            nextRequestId(),
            uuid,
            position.x(),
            position.y(),
            position.z(),
            blockFace
        );
        send(command);
    }

    MenuSnapshot menuSnapshot(UUID uuid)
    {
        final GetOpenMenu.Command command = new GetOpenMenu.Command(nextRequestId(), uuid);
        final GetOpenMenu.Response response = send(command);
        if (!response.open())
            return new MenuSnapshot(false, "", List.of());

        try
        {
            final MenuItemSnapshot[] items = objectMapper.readValue(
                Objects.requireNonNullElse(response.itemsJson(), "[]"), MenuItemSnapshot[].class);
            return new MenuSnapshot(true, Objects.requireNonNullElse(response.title(), ""), List.of(items));
        }
        catch (JacksonException exception)
        {
            throw new IllegalStateException("Failed to parse menu snapshot JSON.", exception);
        }
    }

    void clickMenuSlot(UUID uuid, int slot)
    {
        final ClickMenuSlot.Command command = new ClickMenuSlot.Command(nextRequestId(), uuid, slot);
        send(command);
    }

    void dragMenuSlots(UUID uuid, String materialKey, int... slots)
    {
        final DragMenuSlots.Command command = new DragMenuSlots.Command(nextRequestId(), uuid, materialKey, slots);
        send(command);
    }

    void waitTicks(int ticks)
    {
        final WaitTicks.Command command = new WaitTicks.Command(nextRequestId(), ticks);
        send(command);
    }

    List<String> playerMessages(UUID uuid)
    {
        final GetPlayerMessages.Command command = new GetPlayerMessages.Command(nextRequestId(), uuid);
        return send(command).messages();
    }

    List<ChatComponentSnapshot> playerChatComponents(UUID uuid)
    {
        final GetPlayerChatComponents.Command command =
            new GetPlayerChatComponents.Command(nextRequestId(), uuid);
        final GetPlayerChatComponents.Response response = send(command);
        try
        {
            return java.util.Arrays.stream(objectMapper.readValue(response.componentsJson(), String[].class))
                .map(ChatComponentSnapshot::new)
                .toList();
        }
        catch (JacksonException exception)
        {
            throw new IllegalStateException("Failed to parse player chat components JSON.", exception);
        }
    }

    long getServerTick()
    {
        final GetServerTick.Command command = new GetServerTick.Command(nextRequestId());
        return send(command).tick();
    }

    boolean teleportPlayer(UUID uuid, String worldName, double x, double y, double z)
    {
        final TeleportPlayer.Command command = new TeleportPlayer.Command(nextRequestId(), uuid, worldName, x, y, z);
        return send(command).teleported();
    }

    boolean loadChunk(String worldName, int x, int z)
    {
        final LoadChunk.Command command = new LoadChunk.Command(nextRequestId(), worldName, x, z);
        return send(command).loaded();
    }

    boolean unloadChunk(String worldName, int x, int z)
    {
        final UnloadChunk.Command command = new UnloadChunk.Command(nextRequestId(), worldName, x, z);
        return send(command).unloaded();
    }

    boolean isChunkLoaded(String worldName, int x, int z)
    {
        final IsChunkLoaded.Command command = new IsChunkLoaded.Command(nextRequestId(), worldName, x, z);
        return send(command).loaded();
    }

    List<Map<String, Object>> getPlayerInventory(UUID uuid)
    {
        final GetPlayerInventory.Command command = new GetPlayerInventory.Command(nextRequestId(), uuid);
        final GetPlayerInventory.Response response = send(command);
        return parseJson(response.inventoryJson(), "inventoryJson", TYPE_INVENTORY_ITEM_LIST);
    }

    boolean dropItem(UUID uuid)
    {
        final DropItem.Command command = new DropItem.Command(nextRequestId(), uuid);
        return send(command).dropped();
    }

    void registerEventListener(String eventClassName)
    {
        final RegisterEventListener.Command command =
            new RegisterEventListener.Command(nextRequestId(), eventClassName);
        send(command);
    }

    List<Map<String, String>> getCapturedEvents(String eventClassName)
    {
        final GetCapturedEvents.Command command = new GetCapturedEvents.Command(nextRequestId(), eventClassName);
        final GetCapturedEvents.Response response = send(command);
        return parseJson(response.eventsJson(), "eventsJson", TYPE_EVENT_DATA_LIST);
    }

    void clearCapturedEvents(String eventClassName)
    {
        final ClearCapturedEvents.Command command = new ClearCapturedEvents.Command(nextRequestId(), eventClassName);
        send(command);
    }

    void unregisterEventListener(String eventClassName)
    {
        final UnregisterEventListener.Command command =
            new UnregisterEventListener.Command(nextRequestId(), eventClassName);
        send(command);
    }

    Platform serverPlatform()
    {
        final GetServerPlatform.Command command = new GetServerPlatform.Command(nextRequestId());
        final GetServerPlatform.Response response = send(command);
        final String platformName = (response.serverName() + " " + response.serverVersion())
            .toLowerCase(java.util.Locale.ROOT);
        if (platformName.contains("paper"))
            return Platform.PAPER;
        if (platformName.contains("spigot") || platformName.contains("craftbukkit"))
            return Platform.SPIGOT;
        return Platform.UNKNOWN;
    }

    synchronized <R extends IAgentResponse> R send(IAgentCommand<R> command)
    {
        if (command.requestId() == null || command.requestId().isBlank())
            throw new IllegalArgumentException("'requestId' must be non-blank.");
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

            final JsonNode root = objectMapper.readTree(responseLine);
            final String responseRequestId = root.path("requestId").asString("unknown");
            final String requestId = command.requestId();

            if (!requestId.equals(responseRequestId))
            {
                throw new IllegalStateException(
                    "Unexpected response id '%s' for request '%s'."
                        .formatted(responseRequestId, requestId)
                );
            }

            if (!root.path("success").asBoolean())
            {
                final AgentErrorCode errorCode = AgentErrorCode.fromWireCode(root.path("errorCode").asString())
                    .orElse(AgentErrorCode.UNKNOWN);
                final String errorMessage = root.path("errorMessage").asString("");
                throw new IllegalStateException(
                    "Agent request failed. code=%s message=%s".formatted(errorCode.wireCode(), errorMessage)
                );
            }

            return objectMapper.treeToValue(root, command.responseType());
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

    /**
     * Deserializes a protocol response's JSON field using the supplied type reference.
     *
     * @param json
     *     JSON value to deserialize.
     * @param key
     *     Response field name used in failure diagnostics.
     * @param typeRef
     *     Jackson type reference for deserialization.
     * @param <T>
     *     Expected return type.
     * @return
     *     Deserialized value.
     * @throws IllegalStateException
     *     When deserialization fails.
     */
    private <T> T parseJson(String json, String key, TypeReference<T> typeRef)
    {
        try
        {
            return objectMapper.readValue(json, typeRef);
        }
        catch (JacksonException exception)
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
