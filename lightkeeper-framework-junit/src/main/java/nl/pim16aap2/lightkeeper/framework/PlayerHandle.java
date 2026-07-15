package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.protocol.DropResult;
import org.bukkit.block.BlockFace;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Handle for a synthetic player.
 * <p>
 * This type exposes common player actions used in end-to-end tests and supports fluent method chaining for
 * orchestration-style tests.
 */
public final class PlayerHandle
{
    private final IFrameworkGatewayView frameworkGateway;
    private final UUID uniqueId;
    private final String name;

    /**
     * Creates a player handle.
     *
     * @param frameworkGateway
     *     Internal gateway for operations.
     * @param uniqueId
     *     Player UUID.
     * @param name
     *     Player name.
     */
    PlayerHandle(IFrameworkGatewayView frameworkGateway, UUID uniqueId, String name)
    {
        this.frameworkGateway = Objects.requireNonNull(frameworkGateway, "frameworkGateway may not be null.");
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId may not be null.");
        this.name = Objects.requireNonNull(name, "name may not be null.");
    }

    /**
     * Gets the synthetic player UUID.
     *
     * @return Player UUID.
     */
    public UUID uniqueId()
    {
        return uniqueId;
    }

    /**
     * Gets the current player name.
     *
     * @return The player name.
     */
    public String name()
    {
        return name;
    }

    /**
     * Executes a command as this player.
     *
     * @param command
     *     Command text. Leading slash is optional.
     * @return This handle for fluent chaining.
     */
    public PlayerHandle executeCommand(String command)
    {
        frameworkGateway.executePlayerCommand(uniqueId, command);
        return this;
    }

    /**
     * Teleports this player to target coordinates in a world.
     *
     * @param world
     *     Target world.
     * @param x
     *     X coordinate.
     * @param y
     *     Y coordinate.
     * @param z
     *     Z coordinate.
     * @return This handle for fluent chaining.
     * @throws IllegalStateException
     *     If the server rejected the teleport (e.g. a plugin cancelled the teleport event).
     */
    public PlayerHandle teleport(WorldHandle world, double x, double y, double z)
    {
        Objects.requireNonNull(world, "world may not be null.");
        if (!frameworkGateway.teleportPlayer(uniqueId, world.name(), x, y, z))
            throw new IllegalStateException(
                "Teleport of player '%s' to [%.2f, %.2f, %.2f] in world '%s' was rejected by the server.".formatted(
                    name, x, y, z, world.name()));
        return this;
    }

    /**
     * Teleports this player to a target position in a world.
     *
     * @param world
     *     Target world.
     * @param position
     *     Target position.
     * @return This handle for fluent chaining.
     * @throws IllegalStateException
     *     If the server rejected the teleport (e.g. a plugin cancelled the teleport event).
     */
    public PlayerHandle teleport(WorldHandle world, Vec3 position)
    {
        Objects.requireNonNull(world, "world may not be null.");
        Objects.requireNonNull(position, "position may not be null.");
        return teleport(world, position.x(), position.y(), position.z());
    }

    /**
     * Places a block from this player perspective.
     *
     * @param materialKey
     *     Material key, for example {@code minecraft:stone} or {@code STONE}.
     * @param x
     *     X coordinate.
     * @param y
     *     Y coordinate.
     * @param z
     *     Z coordinate.
     * @return This handle for fluent chaining.
     */
    public PlayerHandle placeBlock(String materialKey, int x, int y, int z)
    {
        frameworkGateway.placePlayerBlock(uniqueId, materialKey, x, y, z);
        return this;
    }

    /**
     * Fires a left-click block interaction at a position in this player's current world.
     *
     * @param x
     *     X coordinate.
     * @param y
     *     Y coordinate.
     * @param z
     *     Z coordinate.
     * @return The interaction result; a real {@code PlayerInteractEvent} is always fired.
     */
    public InteractionResult leftClickBlock(int x, int y, int z)
    {
        return leftClickBlock(new BlockPos(x, y, z));
    }

    /**
     * Fires a left-click block interaction at a position in this player's current world.
     *
     * @param position
     *     Block coordinates.
     * @return The interaction result; a real {@code PlayerInteractEvent} is always fired.
     */
    public InteractionResult leftClickBlock(BlockPos position)
    {
        return leftClickBlock(position, BlockFace.UP);
    }

    /**
     * Fires a left-click block interaction at a position in this player's current world.
     *
     * @param position
     *     Block coordinates.
     * @return The interaction result; a real {@code PlayerInteractEvent} is always fired.
     * @deprecated Use {@link #leftClickBlock(BlockPos)} instead.
     */
    @Deprecated(forRemoval = true)
    public InteractionResult leftClickBlock(Vector3Di position)
    {
        Objects.requireNonNull(position, "position may not be null.");
        return leftClickBlock(position.toBlockPos());
    }

    /**
     * Fires a left-click block interaction at a position in this player's current world.
     *
     * @param position
     *     Block coordinates.
     * @param blockFace
     *     Clicked block face.
     * @return The interaction result; a real {@code PlayerInteractEvent} is always fired.
     */
    public InteractionResult leftClickBlock(BlockPos position, BlockFace blockFace)
    {
        final boolean cancelled = frameworkGateway.leftClickBlock(
            uniqueId,
            Objects.requireNonNull(position, "position may not be null."),
            Objects.requireNonNull(blockFace, "blockFace may not be null.").name()
        );
        return new InteractionResult(true, cancelled);
    }

    /**
     * Fires a left-click block interaction at a position in this player's current world.
     *
     * @param position
     *     Block coordinates.
     * @param blockFace
     *     Clicked block face.
     * @return The interaction result; a real {@code PlayerInteractEvent} is always fired.
     * @deprecated Use {@link #leftClickBlock(BlockPos, BlockFace)} instead.
     */
    @Deprecated(forRemoval = true)
    public InteractionResult leftClickBlock(Vector3Di position, BlockFace blockFace)
    {
        Objects.requireNonNull(position, "position may not be null.");
        return leftClickBlock(position.toBlockPos(), blockFace);
    }

    /**
     * Fires a right-click block interaction at a position in this player's current world.
     *
     * @param x
     *     X coordinate.
     * @param y
     *     Y coordinate.
     * @param z
     *     Z coordinate.
     * @return The interaction result; a real {@code PlayerInteractEvent} is always fired.
     */
    public InteractionResult rightClickBlock(int x, int y, int z)
    {
        return rightClickBlock(new BlockPos(x, y, z));
    }

    /**
     * Fires a right-click block interaction at a position in this player's current world.
     *
     * @param position
     *     Block coordinates.
     * @return The interaction result; a real {@code PlayerInteractEvent} is always fired.
     */
    public InteractionResult rightClickBlock(BlockPos position)
    {
        return rightClickBlock(position, BlockFace.UP);
    }

    /**
     * Fires a right-click block interaction at a position in this player's current world.
     *
     * @param position
     *     Block coordinates.
     * @return The interaction result; a real {@code PlayerInteractEvent} is always fired.
     * @deprecated Use {@link #rightClickBlock(BlockPos)} instead.
     */
    @Deprecated(forRemoval = true)
    public InteractionResult rightClickBlock(Vector3Di position)
    {
        Objects.requireNonNull(position, "position may not be null.");
        return rightClickBlock(position.toBlockPos());
    }

    /**
     * Fires a right-click block interaction at a position in this player's current world.
     *
     * @param position
     *     Block coordinates.
     * @param blockFace
     *     Clicked block face.
     * @return The interaction result; a real {@code PlayerInteractEvent} is always fired.
     */
    public InteractionResult rightClickBlock(BlockPos position, BlockFace blockFace)
    {
        final boolean cancelled = frameworkGateway.rightClickBlock(
            uniqueId,
            Objects.requireNonNull(position, "position may not be null."),
            Objects.requireNonNull(blockFace, "blockFace may not be null.").name()
        );
        return new InteractionResult(true, cancelled);
    }

    /**
     * Fires a right-click block interaction at a position in this player's current world.
     *
     * @param position
     *     Block coordinates.
     * @param blockFace
     *     Clicked block face.
     * @return The interaction result; a real {@code PlayerInteractEvent} is always fired.
     * @deprecated Use {@link #rightClickBlock(BlockPos, BlockFace)} instead.
     */
    @Deprecated(forRemoval = true)
    public InteractionResult rightClickBlock(Vector3Di position, BlockFace blockFace)
    {
        Objects.requireNonNull(position, "position may not be null.");
        return rightClickBlock(position.toBlockPos(), blockFace);
    }

    /**
     * Makes this player say a chat message, firing the real chat event (synchronously, as with any
     * plugin-triggered chat).
     *
     * @param message
     *     The chat message.
     * @return This handle for fluent chaining.
     */
    public PlayerHandle chat(String message)
    {
        frameworkGateway.playerChat(uniqueId, message);
        return this;
    }

    /**
     * Gets the permission control handle for this player.
     *
     * <p>Mutations apply to LightKeeper's own permission attachment and bypass permission plugins; see
     * {@link PermissionControl} for the full contract.
     *
     * @return A permission control handle bound to this player.
     */
    public PermissionControl permissions()
    {
        return new PermissionControl(frameworkGateway, uniqueId);
    }

    /**
     * Gets a snapshot of this player's inventory.
     *
     * @return Inventory snapshot.
     */
    public InventorySnapshot inventory()
    {
        return frameworkGateway.playerInventory(uniqueId);
    }

    /**
     * Simulates the player dropping their main hand item.
     *
     * <p>An empty main hand ({@link DropResult#EMPTY_HAND}) is reported distinctly from a plugin cancelling the
     * drop event ({@link DropResult#CANCELLED}); no event is fired at all for an empty hand. On a cancelled
     * drop, the item entity is created and removed again to satisfy the Bukkit event contract, and the
     * inventory is unchanged.
     *
     * @return The drop outcome.
     */
    public DropResult dropMainHandItem()
    {
        return frameworkGateway.dropItem(uniqueId);
    }

    /**
     * Waits for at least the requested number of server ticks.
     *
     * @param ticks
     *     Tick count to wait.
     * @return This handle for fluent chaining.
     */
    public PlayerHandle andWaitTicks(int ticks)
    {
        frameworkGateway.waitTicks(ticks);
        return this;
    }

    /**
     * Waits for an open menu for this player.
     *
     * @param timeoutSeconds
     *     Timeout in seconds.
     * @return A menu handle bound to this player.
     */
    public MenuHandle andWaitForMenuOpen(int timeoutSeconds)
    {
        final MenuHandle menuHandle = FrameworkHandleFactory.menuHandle(frameworkGateway, this);
        frameworkGateway.waitUntil(
            () -> menuHandle.snapshot().open(),
            Duration.ofSeconds(timeoutSeconds)
        );
        return menuHandle;
    }

    /**
     * Waits for an open menu using the default timeout.
     *
     * @return A menu handle bound to this player.
     */
    public MenuHandle andWaitForMenuOpen()
    {
        final MenuHandle menuHandle = FrameworkHandleFactory.menuHandle(frameworkGateway, this);
        frameworkGateway.waitUntil(
            () -> menuHandle.snapshot().open(),
            MenuHandle.DEFAULT_MENU_WAIT_TIMEOUT
        );
        return menuHandle;
    }

    /**
     * Returns the currently open menu if available.
     *
     * @return The open menu handle, or {@code null} when no actionable menu is open.
     */
    public @Nullable MenuHandle getMenu()
    {
        final MenuHandle menuHandle = FrameworkHandleFactory.menuHandle(frameworkGateway, this);
        return menuHandle.snapshot().open() ? menuHandle : null;
    }

    /**
     * Gets all recorded messages received by this player.
     *
     * @return Snapshot of received messages, ordered oldest-to-newest.
     */
    public List<String> receivedMessages()
    {
        return frameworkGateway.playerMessages(uniqueId);
    }

    /**
     * Gets a list of captured chat components for this player.
     *
     * @return Captured chat components.
     */
    public List<ChatComponentSnapshot> chatComponents()
    {
        return frameworkGateway.playerChatComponents(uniqueId);
    }

    /**
     * Gets all recorded messages flattened into a single multi-line string.
     *
     * @return Received messages joined with line separators.
     */
    public String receivedMessagesText()
    {
        return String.join(System.lineSeparator(), receivedMessages());
    }

    /**
     * Removes this synthetic player from the server.
     */
    public void remove()
    {
        frameworkGateway.removePlayer(this);
    }
}
