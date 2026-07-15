package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.nms.api.BotLoginRequest;
import nl.pim16aap2.lightkeeper.nms.api.IBotLoginOutcome;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import nl.pim16aap2.lightkeeper.protocol.CreatePlayer;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.HasPlayerPermission;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlock;
import nl.pim16aap2.lightkeeper.protocol.MutatePlayerPermission;
import nl.pim16aap2.lightkeeper.protocol.PlacePlayerBlock;
import nl.pim16aap2.lightkeeper.protocol.PlayerChat;
import nl.pim16aap2.lightkeeper.protocol.RemovePlayer;
import nl.pim16aap2.lightkeeper.protocol.RightClickBlock;
import nl.pim16aap2.lightkeeper.protocol.TeleportPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Protocol action handler for synthetic player lifecycle, movement, and world interactions.
 *
 * <p>This class is responsible for creating/removing synthetic players, executing commands as those players,
 * placing blocks through player context, and teleporting players.
 */
final class AgentPlayerActions
{
    /**
     * Owning plugin used for logging and permission attachment scope.
     */
    private final JavaPlugin plugin;
    /**
     * Scheduler bridge to execute Bukkit mutations on the main server thread.
     */
    private final AgentMainThreadExecutor mainThreadExecutor;
    /**
     * Synthetic player state and message registry.
     */
    private final AgentSyntheticPlayerStore playerStore;
    /**
     * NMS-backed synthetic player implementation.
     */
    private final IBotPlayerNmsAdapter botPlayerNmsAdapter;

    /**
     * @param plugin
     *     Plugin context for logger and permission attachment scope.
     * @param mainThreadExecutor
     *     Main-thread execution bridge for Bukkit-safe operations.
     * @param playerStore
     *     Registry containing synthetic players and related state.
     * @param botPlayerNmsAdapter
     *     NMS adapter used to spawn/remove synthetic players and drain received messages.
     */
    AgentPlayerActions(
        JavaPlugin plugin,
        AgentMainThreadExecutor mainThreadExecutor,
        AgentSyntheticPlayerStore playerStore,
        IBotPlayerNmsAdapter botPlayerNmsAdapter)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.playerStore = Objects.requireNonNull(playerStore, "playerStore");
        this.botPlayerNmsAdapter = Objects.requireNonNull(botPlayerNmsAdapter, "botPlayerNmsAdapter");
    }

    /**
     * Handles {@code CREATE_PLAYER} by routing to the full-login pipeline or the legacy spawn path.
     *
     * @param command
     *     Typed command carrying player name, UUID, world, spawn coordinates, health, permissions, join mode,
     *     and locale.
     * @return Response containing the created player's UUID and name.
     *
     * @throws Exception
     *     Propagates validation, login, and main-thread execution failures.
     */
    CreatePlayer.Response handleCreatePlayer(CreatePlayer.Command command)
        throws Exception
    {
        return switch (command.joinMode())
        {
            case FULL_LOGIN -> handleFullLoginPlayer(command);
            case LEGACY_SPAWN -> handleLegacySpawnPlayer(command);
        };
    }

    /**
     * Spawns a synthetic player through the internal legacy spawn path (main thread).
     *
     * @param command
     *     Typed create-player command in {@code LEGACY_SPAWN} mode (a non-null UUID is guaranteed by validation).
     * @return Response containing the created player's UUID and name.
     *
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    private CreatePlayer.Response handleLegacySpawnPlayer(CreatePlayer.Command command)
        throws Exception
    {
        final String name = command.name();
        final UUID uuid = Objects.requireNonNull(command.uuid(), "uuid may not be null under LEGACY_SPAWN.");
        final String worldName = command.worldName();
        final Double x = command.x();
        final Double y = command.y();
        final Double z = command.z();
        final Double health = command.health();
        final String permissionsCsv = command.permissionsCsv();

        final Player player = mainThreadExecutor.callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));

            final Location spawnLocation = x == null || y == null || z == null
                ? world.getSpawnLocation()
                : new Location(world, x, y, z);
            final Player spawnedPlayer = botPlayerNmsAdapter.spawnPlayer(uuid, name, world, spawnLocation);
            if (health != null)
                spawnedPlayer.setHealth(Math.min(spawnedPlayer.getMaxHealth(), health));

            // Register before applying permissions so setPermissions can store the attachment on the player's
            // state; registering afterwards leaves the attachment created-and-applied but never recorded, so it
            // can never be revoked on removal.
            playerStore.registerSyntheticPlayer(uuid, spawnedPlayer);

            if (permissionsCsv != null && !permissionsCsv.isBlank())
                playerStore.setPermissions(plugin, uuid, spawnedPlayer, permissionsCsv);

            return spawnedPlayer;
        });

        plugin.getLogger().info(
            "LK_AGENT: Spawned synthetic player '%s' (%s) in world '%s'."
                .formatted(player.getName(), player.getUniqueId(), worldName)
        );
        return new CreatePlayer.Response(player.getUniqueId(), player.getName());
    }

    /**
     * Joins a synthetic player through the full vanilla login pipeline.
     *
     * <p>The driver runs off the main thread (it fires {@code AsyncPlayerPreLoginEvent} on the connection
     * thread; the join completes on the main thread), so this method blocks the calling connection thread — not
     * the server main thread — while awaiting the {@code PlayerJoinEvent} for the bot's name. Whitelist/ban/full/
     * plugin denials and timeouts are surfaced as typed {@link AgentProtocolException}s.
     *
     * <p>The driver wait and the join-event wait share ONE deadline bounded by the agent's sync-operation
     * timeout, preserving the {@code RuntimeProtocol} invariant that the agent reports its own detailed
     * {@code TIMEOUT} before the client watchdog (timeout + margin) gives up.
     *
     * @param command
     *     Typed create-player command in {@code FULL_LOGIN} mode.
     * @return Response containing the server-assigned offline UUID and name.
     *
     * @throws Exception
     *     Propagates login failures and main-thread execution failures.
     */
    private CreatePlayer.Response handleFullLoginPlayer(CreatePlayer.Command command)
        throws Exception
    {
        if (Bukkit.isPrimaryThread())
            throw new IllegalStateException(
                "FULL_LOGIN joins must not run on the server main thread: the join blocks on PlayerJoinEvent, "
                    + "which fires on the main thread, so this would deadlock.");

        final String name = command.name();
        final String locale = command.locale();
        final Double health = command.health();
        final String permissionsCsv = command.permissionsCsv();
        final long timeoutSeconds = mainThreadExecutor.syncOperationTimeoutSeconds();
        // One shared deadline for the whole FULL_LOGIN join (driver + join-event wait).
        final long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);

        final CountDownLatch joinLatch = new CountDownLatch(1);
        final AtomicReference<Player> joinedPlayer = new AtomicReference<>();
        final Listener listener = new Listener()
        {
        };
        final EventExecutor executor = (ignoredListener, event) ->
        {
            if (event instanceof PlayerJoinEvent joinEvent && joinEvent.getPlayer().getName().equals(name))
            {
                joinedPlayer.set(joinEvent.getPlayer());
                joinLatch.countDown();
            }
        };
        mainThreadExecutor.callOnMainThread(() ->
        {
            Bukkit.getPluginManager().registerEvent(
                PlayerJoinEvent.class, listener, EventPriority.MONITOR, executor, plugin);
            return Boolean.TRUE;
        });

        try
        {
            final int port = Bukkit.getServer().getPort();
            final IBotLoginOutcome outcome = botPlayerNmsAdapter.loginDriver().login(
                new BotLoginRequest(name, port, locale, Duration.ofSeconds(timeoutSeconds)));

            if (outcome instanceof IBotLoginOutcome.Denied denied)
                throw new AgentProtocolException(
                    AgentErrorCode.PLAYER_JOIN_DENIED,
                    "Bot '%s' was denied during %s: %s".formatted(name, denied.phase(), denied.reason()));
            if (outcome instanceof IBotLoginOutcome.TimedOut)
                throw new AgentProtocolException(
                    AgentErrorCode.PLAYER_JOIN_TIMEOUT,
                    "Bot '%s' did not complete the login pipeline within %d seconds.".formatted(name, timeoutSeconds));

            // The join event gets only the time remaining on the shared deadline, never a fresh budget: the
            // driver has already reached the play phase, so the event wait is normally near-instant.
            final long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L || !joinLatch.await(remainingNanos, TimeUnit.NANOSECONDS))
                throw new AgentProtocolException(
                    AgentErrorCode.PLAYER_JOIN_TIMEOUT,
                    "Bot '%s' completed login but no PlayerJoinEvent fired within the %d-second join budget."
                        .formatted(name, timeoutSeconds));

            final Player player = Objects.requireNonNull(joinedPlayer.get(), "joined player");
            registerJoinedPlayer(player, health, permissionsCsv);

            plugin.getLogger().info(
                "LK_AGENT: Full-login player '%s' (%s) joined.".formatted(player.getName(), player.getUniqueId()));
            return new CreatePlayer.Response(player.getUniqueId(), player.getName());
        }
        finally
        {
            mainThreadExecutor.callOnMainThread(() ->
            {
                HandlerList.unregisterAll(listener);
                return Boolean.TRUE;
            });
        }
    }

    /**
     * Registers a freshly joined full-login player in the store and applies health/permissions on the main thread.
     *
     * @param player
     *     The joined Bukkit player.
     * @param health
     *     Starting health, or {@code null} to leave the server default.
     * @param permissionsCsv
     *     Comma-separated permission nodes to grant, or {@code null} for none.
     *
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    private void registerJoinedPlayer(Player player, @Nullable Double health, @Nullable String permissionsCsv)
        throws Exception
    {
        final UUID uuid = player.getUniqueId();
        mainThreadExecutor.callOnMainThread(() ->
        {
            playerStore.registerSyntheticPlayer(uuid, player);
            if (health != null)
                player.setHealth(Math.min(player.getMaxHealth(), health));
            if (permissionsCsv != null && !permissionsCsv.isBlank())
                playerStore.setPermissions(plugin, uuid, player, permissionsCsv);
            return Boolean.TRUE;
        });
    }

    /**
     * Handles {@code REMOVE_PLAYER} by removing permissions, despawning the synthetic player, and cleaning state.
     *
     * @param command
     *     Typed command carrying the player UUID.
     * @return Response when cleanup completes.
     *
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    RemovePlayer.Response handleRemovePlayer(RemovePlayer.Command command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            playerStore.removePermissionAttachment(uuid, player);
            botPlayerNmsAdapter.removePlayer(player);
            playerStore.removeSyntheticPlayer(uuid);
            plugin.getLogger().info(
                "LK_AGENT: Removed synthetic player '%s' (%s)."
                    .formatted(player.getName(), player.getUniqueId())
            );
            return Boolean.TRUE;
        });

        return new RemovePlayer.Response();
    }

    /**
     * Handles {@code EXECUTE_PLAYER_COMMAND} by dispatching the command in the player's execution context.
     *
     * @param req
     *     Typed command carrying the player UUID and command string.
     * @return Response containing whether dispatch succeeded.
     *
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    ExecutePlayerCommand.Response handleExecutePlayerCommand(ExecutePlayerCommand.Command req)
        throws Exception
    {
        final UUID uuid = req.uuid();
        final String rawCommand = req.command();
        final String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean dispatched = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            // performCommand only runs commands the player context knows; fall back to the server dispatcher so
            // commands reachable only through Bukkit.dispatchCommand still execute (parity with the flat branch).
            if (player.performCommand(command))
                return Boolean.TRUE;
            return Bukkit.dispatchCommand(player, command);
        });

        return new ExecutePlayerCommand.Response(dispatched);
    }

    /**
     * Handles {@code PLACE_PLAYER_BLOCK} by setting the target block type in the player's current world.
     *
     * @param command
     *     Typed command carrying UUID, material key, and block coordinates.
     * @return Response containing the resulting block material key.
     *
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    PlacePlayerBlock.Response handlePlacePlayerBlock(PlacePlayerBlock.Command command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String materialKey = command.materialKey();
        final int x = command.x();
        final int y = command.y();
        final int z = command.z();
        final Material material = AgentRequestParsers.parseMaterial(materialKey);
        if (material == null)
            throw new IllegalArgumentException("Unknown material '%s'.".formatted(materialKey));

        final String finalMaterial = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final World world = player.getWorld();
            world.getBlockAt(x, y, z).setType(material);
            return world.getBlockAt(x, y, z).getType().getKey().toString();
        });

        return new PlacePlayerBlock.Response(finalMaterial);
    }

    /**
     * Handles {@code LEFT_CLICK_BLOCK} by firing a synthetic left-click block interaction event.
     *
     * @param command
     *     Typed command carrying UUID, block coordinates, and block face.
     * @return Response containing whether the fired interaction event was cancelled.
     *
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    LeftClickBlock.Response handleLeftClickBlock(LeftClickBlock.Command command)
        throws Exception
    {
        final boolean cancelled = handleClickBlock(
            command.uuid(), command.x(), command.y(), command.z(), command.blockFace(), Action.LEFT_CLICK_BLOCK
        );
        return new LeftClickBlock.Response(cancelled);
    }

    /**
     * Handles {@code RIGHT_CLICK_BLOCK} by firing a synthetic right-click block interaction event.
     *
     * @param command
     *     Typed command carrying UUID, block coordinates, and block face.
     * @return Response containing whether the fired interaction event was cancelled.
     *
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    RightClickBlock.Response handleRightClickBlock(RightClickBlock.Command command)
        throws Exception
    {
        final boolean cancelled = handleClickBlock(
            command.uuid(), command.x(), command.y(), command.z(), command.blockFace(), Action.RIGHT_CLICK_BLOCK
        );
        return new RightClickBlock.Response(cancelled);
    }

    /**
     * Handles {@code TELEPORT_PLAYER} by teleporting a synthetic player to the given world coordinates.
     *
     * @param command
     *     Typed command carrying player UUID, world name, and target coordinates.
     * @return Response with {@code teleported} result.
     *
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    TeleportPlayer.Response handleTeleportPlayer(TeleportPlayer.Command command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String worldName = command.worldName();
        final double x = command.x();
        final double y = command.y();
        final double z = command.z();

        if (worldName.isBlank())
            throw new IllegalArgumentException("Argument 'worldName' must not be blank.");

        final Boolean teleported = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            return player.teleport(new Location(world, x, y, z));
        });

        return new TeleportPlayer.Response(teleported);
    }

    /**
     * Handles {@code MUTATE_PLAYER_PERMISSION} by granting, revoking, or unsetting a single permission node on
     * the player's LightKeeper-managed permission attachment.
     *
     * @param command
     *     Typed command carrying player UUID, permission node, and mutation mode.
     * @return Response when the mutation has been applied.
     *
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    MutatePlayerPermission.Response handleMutatePlayerPermission(MutatePlayerPermission.Command command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String permission = command.permission();
        final MutatePlayerPermission.Mode mode = command.mode();

        mainThreadExecutor.callOnMainThread(() ->
        {
            // The store methods validate registration themselves; only the set paths need the player instance.
            switch (mode)
            {
                case GRANT ->
                    playerStore.setPermission(plugin, uuid, playerStore.getRequiredPlayer(uuid), permission, true);
                case REVOKE ->
                    playerStore.setPermission(plugin, uuid, playerStore.getRequiredPlayer(uuid), permission, false);
                case UNSET -> playerStore.unsetPermission(uuid, permission);
            }
            return Boolean.TRUE;
        });

        return new MutatePlayerPermission.Response();
    }

    /**
     * Handles {@code HAS_PLAYER_PERMISSION} by querying the live permission value on the player.
     *
     * @param command
     *     Typed command carrying player UUID and permission node.
     * @return Response containing the live {@code hasPermission} result.
     *
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    HasPlayerPermission.Response handleHasPlayerPermission(HasPlayerPermission.Command command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String permission = command.permission();

        final Boolean value = mainThreadExecutor.callOnMainThread(
            () -> playerStore.getRequiredPlayer(uuid).hasPermission(permission)
        );

        return new HasPlayerPermission.Response(value);
    }

    /**
     * Handles {@code PLAYER_CHAT} by making the player say a chat message on the main thread.
     *
     * <p>A plugin-triggered {@code Player#chat} fires the chat event synchronously, so routing through the
     * main thread satisfies Bukkit's sync-event threading rule.
     *
     * @param command
     *     Typed command carrying player UUID and message.
     * @return Response when the chat has been dispatched.
     *
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    PlayerChat.Response handlePlayerChat(PlayerChat.Command command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String message = command.message();

        mainThreadExecutor.callOnMainThread(() ->
        {
            playerStore.getRequiredPlayer(uuid).chat(message);
            return Boolean.TRUE;
        });

        return new PlayerChat.Response();
    }

    /**
     * Best-effort shutdown cleanup for all registered synthetic players.
     *
     * <p>Failures for individual players are logged and do not abort cleanup of other players.
     */
    void cleanupSyntheticPlayers()
    {
        for (final UUID uuid : playerStore.syntheticPlayerIds())
        {
            try
            {
                final Player player = playerStore.getRequiredPlayer(uuid);
                playerStore.removePermissionAttachment(uuid, player);
                botPlayerNmsAdapter.removePlayer(player);
                playerStore.removeSyntheticPlayer(uuid);
            }
            catch (Exception exception)
            {
                plugin.getLogger().warning(
                    "Failed to cleanup synthetic player '%s': %s"
                        .formatted(
                            uuid,
                            Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName())
                        )
                );
            }
        }
    }

    /**
     * Fires a synthetic block-click interaction event and returns whether it was cancelled.
     *
     * @param uuid
     *     Player performing the click.
     * @param x
     *     Target block X coordinate.
     * @param y
     *     Target block Y coordinate.
     * @param z
     *     Target block Z coordinate.
     * @param blockFaceName
     *     Bukkit {@code BlockFace} enum name.
     * @param action
     *     Bukkit {@code Action} for left or right click.
     * @return {@code true} if the event was cancelled.
     *
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    private boolean handleClickBlock(UUID uuid, int x, int y, int z, String blockFaceName, Action action)
        throws Exception
    {
        final BlockFace blockFace = AgentRequestParsers.parseBlockFace(blockFaceName);
        if (blockFace == null)
            throw new IllegalArgumentException("Unknown block face '%s'.".formatted(blockFaceName));

        return mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final Block block = player.getWorld().getBlockAt(x, y, z);
            final ItemStack item = player.getInventory().getItemInMainHand();
            final PlayerInteractEvent event = new PlayerInteractEvent(
                player,
                action,
                item,
                block,
                blockFace,
                EquipmentSlot.HAND
            );
            Bukkit.getPluginManager().callEvent(event);
            return event.isCancelled();
        });
    }
}
