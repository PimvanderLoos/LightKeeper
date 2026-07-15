package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import nl.pim16aap2.lightkeeper.framework.Platform;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.BlockType;
import nl.pim16aap2.lightkeeper.protocol.CancelNextEvents;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.ClearServerErrors;
import nl.pim16aap2.lightkeeper.protocol.ClickMenuSlot;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.CreatePlayer;
import nl.pim16aap2.lightkeeper.protocol.DragMenuSlots;
import nl.pim16aap2.lightkeeper.protocol.DropItem;
import nl.pim16aap2.lightkeeper.protocol.DropResult;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommand;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.GetOpenMenu;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerChatComponents;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerInventory;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerMessages;
import nl.pim16aap2.lightkeeper.protocol.GetServerErrors;
import nl.pim16aap2.lightkeeper.protocol.GetServerPlatform;
import nl.pim16aap2.lightkeeper.protocol.GetServerTick;
import nl.pim16aap2.lightkeeper.protocol.Handshake;
import nl.pim16aap2.lightkeeper.protocol.HasPlayerPermission;
import nl.pim16aap2.lightkeeper.protocol.IAgentCommand;
import nl.pim16aap2.lightkeeper.protocol.IAgentResponse;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoaded;
import nl.pim16aap2.lightkeeper.protocol.ItemSnapshot;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlock;
import nl.pim16aap2.lightkeeper.protocol.LoadChunk;
import nl.pim16aap2.lightkeeper.protocol.MainWorld;
import nl.pim16aap2.lightkeeper.protocol.MutatePlayerPermission;
import nl.pim16aap2.lightkeeper.protocol.NewWorld;
import nl.pim16aap2.lightkeeper.protocol.PlacePlayerBlock;
import nl.pim16aap2.lightkeeper.protocol.PlayerChat;
import nl.pim16aap2.lightkeeper.protocol.QueryEntities;
import nl.pim16aap2.lightkeeper.protocol.RegisterEventListener;
import nl.pim16aap2.lightkeeper.protocol.RemovePlayer;
import nl.pim16aap2.lightkeeper.protocol.RightClickBlock;
import nl.pim16aap2.lightkeeper.protocol.SetBlock;
import nl.pim16aap2.lightkeeper.protocol.TeleportPlayer;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunk;
import nl.pim16aap2.lightkeeper.protocol.UnregisterEventListener;
import nl.pim16aap2.lightkeeper.protocol.WaitTicks;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Typed agent RPC client backed by a Unix Domain Socket transport.
 *
 * <p>Each public method serializes a typed {@link IAgentCommand} to JSON, writes it to the socket, reads back
 * a command through {@link UdsAgentTransport} and maps the typed response into framework values.
 */
final class UdsAgentClient implements AutoCloseable
{
    private static final System.Logger LOG = System.getLogger(UdsAgentClient.class.getName());

    /**
     * Default response timeout: the agent's own synchronous-operation timeout plus a safety margin, so the
     * agent reports a detailed {@code TIMEOUT} error before this client-side watchdog closes the channel.
     *
     * <p>Unix Domain Sockets do not support SO_TIMEOUT, so the transport enforces this with a watchdog.
     */
    private static final long DEFAULT_SEND_TIMEOUT_MS =
        RuntimeProtocol.DEFAULT_SYNC_OPERATION_TIMEOUT_SECONDS * 1_000L
            + RuntimeProtocol.CLIENT_RESPONSE_TIMEOUT_MARGIN_MILLIS;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UdsAgentTransport transport;
    private final AtomicLong requestCounter = new AtomicLong(0L);

    UdsAgentClient(Path socketPath, Duration connectTimeout)
    {
        this(socketPath, connectTimeout, DEFAULT_SEND_TIMEOUT_MS);
    }

    /**
     * Creates a client with an explicit response timeout, primarily so tests can drive the watchdog quickly.
     *
     * @param socketPath
     *     Path of the Unix domain socket to connect to.
     * @param connectTimeout
     *     How long to keep retrying the initial connection.
     * @param sendTimeoutMillis
     *     Maximum time to wait for a single response before the watchdog closes the channel.
     */
    UdsAgentClient(Path socketPath, Duration connectTimeout, long sendTimeoutMillis)
    {
        this.transport = new UdsAgentTransport(socketPath, connectTimeout, sendTimeoutMillis);
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

    String blockType(String worldName, BlockPos position)
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

    void setBlock(String worldName, BlockPos position, String material)
    {
        final SetBlock.Command command = new SetBlock.Command(
            nextRequestId(),
            worldName,
            position.x(),
            position.y(),
            position.z(),
            material,
            null
        );
        send(command);
    }

    void setBlockData(String worldName, BlockPos position, String blockData)
    {
        final int bracketStart = blockData.indexOf('[');
        final String materialKey = bracketStart < 0 ? blockData : blockData.substring(0, bracketStart);
        final SetBlock.Command command = new SetBlock.Command(
            nextRequestId(),
            worldName,
            position.x(),
            position.y(),
            position.z(),
            materialKey,
            blockData
        );
        send(command);
    }

    String blockData(String worldName, BlockPos position)
    {
        final BlockType.Command command = new BlockType.Command(
            nextRequestId(),
            worldName,
            position.x(),
            position.y(),
            position.z()
        );
        return send(command).blockData();
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

    void mutatePlayerPermission(UUID uuid, String permission, MutatePlayerPermission.Mode mode)
    {
        final MutatePlayerPermission.Command command =
            new MutatePlayerPermission.Command(nextRequestId(), uuid, permission, mode);
        send(command);
    }

    boolean hasPlayerPermission(UUID uuid, String permission)
    {
        final HasPlayerPermission.Command command =
            new HasPlayerPermission.Command(nextRequestId(), uuid, permission);
        return send(command).value();
    }

    void placePlayerBlock(UUID uuid, String material, int x, int y, int z)
    {
        final PlacePlayerBlock.Command command = new PlacePlayerBlock.Command(nextRequestId(), uuid, material, x, y, z);
        send(command);
    }

    boolean leftClickBlock(UUID uuid, BlockPos position, String blockFace)
    {
        final LeftClickBlock.Command command = new LeftClickBlock.Command(
            nextRequestId(),
            uuid,
            position.x(),
            position.y(),
            position.z(),
            blockFace
        );
        return send(command).cancelled();
    }

    boolean rightClickBlock(UUID uuid, BlockPos position, String blockFace)
    {
        final RightClickBlock.Command command = new RightClickBlock.Command(
            nextRequestId(),
            uuid,
            position.x(),
            position.y(),
            position.z(),
            blockFace
        );
        return send(command).cancelled();
    }

    MenuSnapshot menuSnapshot(UUID uuid)
    {
        final GetOpenMenu.Command command = new GetOpenMenu.Command(nextRequestId(), uuid);
        final GetOpenMenu.Response response = send(command);
        if (!response.open())
            return new MenuSnapshot(false, "", List.of());

        final List<MenuItemSnapshot> items = response.items().stream()
            .map(UdsAgentClient::toMenuItemSnapshot)
            .toList();
        return new MenuSnapshot(true, Objects.requireNonNullElse(response.title(), ""), items);
    }

    private static MenuItemSnapshot toMenuItemSnapshot(ItemSnapshot item)
    {
        return new MenuItemSnapshot(
            item.slot(), item.materialKey(), Objects.requireNonNullElse(item.displayName(), ""), item.lore());
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

    List<ItemSnapshot> getPlayerInventory(UUID uuid)
    {
        final GetPlayerInventory.Command command = new GetPlayerInventory.Command(nextRequestId(), uuid);
        return send(command).items();
    }

    DropResult dropItem(UUID uuid)
    {
        final DropItem.Command command = new DropItem.Command(nextRequestId(), uuid);
        return send(command).result();
    }

    void registerEventListener(String eventClassName)
    {
        final RegisterEventListener.Command command =
            new RegisterEventListener.Command(nextRequestId(), eventClassName);
        send(command);
    }

    List<GetCapturedEvents.CapturedEvent> getCapturedEvents(String eventClassName)
    {
        final GetCapturedEvents.Command command = new GetCapturedEvents.Command(nextRequestId(), eventClassName);
        return send(command).events();
    }

    void cancelNextEvents(String eventClassName, int count)
    {
        final CancelNextEvents.Command command =
            new CancelNextEvents.Command(nextRequestId(), eventClassName, count);
        send(command);
    }

    void playerChat(UUID uuid, String message)
    {
        final PlayerChat.Command command = new PlayerChat.Command(nextRequestId(), uuid, message);
        send(command);
    }

    QueryEntities.Response queryEntities(
        String worldName,
        @Nullable String entityTypeKey,
        @Nullable BlockPos boundsMin,
        @Nullable BlockPos boundsMax,
        boolean countOnly)
    {
        if ((boundsMin == null) != (boundsMax == null))
            throw new IllegalArgumentException("Bounds must be both present or both absent.");
        if (boundsMin != null && boundsMax != null)
        {
            return send(new QueryEntities.Command(
                nextRequestId(),
                worldName,
                entityTypeKey,
                true,
                boundsMin.x(),
                boundsMin.y(),
                boundsMin.z(),
                boundsMax.x(),
                boundsMax.y(),
                boundsMax.z(),
                countOnly
            ));
        }
        return send(new QueryEntities.Command(
            nextRequestId(),
            worldName,
            entityTypeKey,
            false,
            0, 0, 0, 0, 0, 0,
            countOnly
        ));
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

    GetServerErrors.Response getServerErrors()
    {
        final GetServerErrors.Command command = new GetServerErrors.Command(nextRequestId());
        return send(command);
    }

    void clearServerErrors()
    {
        final ClearServerErrors.Command command = new ClearServerErrors.Command(nextRequestId());
        send(command);
    }

    Platform serverPlatform()
    {
        final GetServerPlatform.Command command = new GetServerPlatform.Command(nextRequestId());
        final GetServerPlatform.Response response = send(command);
        try
        {
            return Platform.valueOf(response.platform());
        }
        catch (IllegalArgumentException | NullPointerException ignored)
        {
            // The agent maps unknown platforms to the explicit "UNKNOWN" token, so any other unmappable value is
            // a protocol bug that would otherwise silently skip platform-conditional tests. Make it visible.
            LOG.log(
                System.Logger.Level.WARNING,
                () -> "Unrecognized server platform '" + response.platform() + "'; treating it as UNKNOWN.");
            return Platform.UNKNOWN;
        }
    }

    <R extends IAgentResponse> R send(IAgentCommand<R> command)
    {
        return transport.send(command);
    }

    synchronized void rehandshake(Duration timeout, String token, int protocolVersion, String agentSha256)
    {
        transport.reconnect(timeout);
        handshake(token, protocolVersion, agentSha256);
    }

    @Override
    public synchronized void close()
    {
        transport.close();
    }

    private String nextRequestId()
    {
        return Long.toString(requestCounter.incrementAndGet());
    }
}
