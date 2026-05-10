package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.CreatePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.DropItemCommand;
import nl.pim16aap2.lightkeeper.protocol.ExecutePlayerCommandCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerChatComponentsCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerInventoryCommand;
import nl.pim16aap2.lightkeeper.protocol.GetPlayerMessagesCommand;
import nl.pim16aap2.lightkeeper.protocol.LeftClickBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.PlacePlayerBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.RemovePlayerCommand;
import nl.pim16aap2.lightkeeper.protocol.RightClickBlockCommand;
import nl.pim16aap2.lightkeeper.protocol.TeleportPlayerCommand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Protocol action handler for synthetic player lifecycle and player-driven interactions.
 *
 * <p>This class is responsible for creating/removing synthetic players, executing commands as those players,
 * placing blocks through player context, and returning captured player-facing messages.
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
     * JSON mapper used to serialize message payloads.
     */
    private final ObjectMapper objectMapper;
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
     * @param objectMapper
     *     JSON serializer for message lists.
     * @param botPlayerNmsAdapter
     *     NMS adapter used to spawn/remove synthetic players and drain received messages.
     */
    AgentPlayerActions(
        JavaPlugin plugin,
        AgentMainThreadExecutor mainThreadExecutor,
        AgentSyntheticPlayerStore playerStore,
        ObjectMapper objectMapper,
        IBotPlayerNmsAdapter botPlayerNmsAdapter)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.playerStore = Objects.requireNonNull(playerStore, "playerStore");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.botPlayerNmsAdapter = Objects.requireNonNull(botPlayerNmsAdapter, "botPlayerNmsAdapter");
    }

    /**
     * Handles {@code CREATE_PLAYER} by spawning a synthetic player and registering it in the store.
     *
     * @param command
     *     Typed command carrying player name, UUID, world, spawn coordinates, health, and permissions.
     * @return
     *     Success response containing the created player's UUID and name, or a validation error response.
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    AgentResponse handleCreatePlayer(CreatePlayerCommand command)
        throws Exception
    {
        final String name = command.name();
        final UUID uuid = command.uuid();
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

            if (permissionsCsv != null && !permissionsCsv.isBlank())
                playerStore.setPermissions(plugin, uuid, spawnedPlayer, permissionsCsv);

            return spawnedPlayer;
        });

        playerStore.registerSyntheticPlayer(uuid, player);

        plugin.getLogger().info(
            "LK_AGENT: Created synthetic player '%s' (%s) in world '%s'."
                .formatted(player.getName(), player.getUniqueId(), worldName)
        );
        return AgentResponses.successResponse(command.requestId(), Map.of(
            "uuid", player.getUniqueId().toString(),
            "name", player.getName()
        ));
    }

    /**
     * Handles {@code REMOVE_PLAYER} by removing permissions, despawning the synthetic player, and cleaning state.
     *
     * @param command
     *     Typed command carrying the player UUID.
     * @return
     *     Success response when cleanup completes.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleRemovePlayer(RemovePlayerCommand command)
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

        return AgentResponses.successResponse(command.requestId(), Map.of("removed", "true"));
    }

    /**
     * Handles {@code EXECUTE_PLAYER_COMMAND} by dispatching the command in the player's execution context.
     *
     * @param command
     *     Typed command carrying the player UUID and command string.
     * @return
     *     Success response containing whether dispatch succeeded.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleExecutePlayerCommand(ExecutePlayerCommandCommand command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String rawCommand = command.command();
        final String cmd = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean success = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            boolean result = player.performCommand(cmd);
            if (!result)
                result = Bukkit.dispatchCommand(player, cmd);
            return result;
        });

        return AgentResponses.successResponse(command.requestId(), Map.of("success", success.toString()));
    }

    /**
     * Handles {@code PLACE_PLAYER_BLOCK} by setting the target block type in the player's current world.
     *
     * @param command
     *     Typed command carrying UUID, material key, and block coordinates.
     * @return
     *     Success response containing the resulting block material key, or validation error when material is unknown.
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    AgentResponse handlePlacePlayerBlock(PlacePlayerBlockCommand command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String materialKey = command.materialKey();
        final int x = command.x();
        final int y = command.y();
        final int z = command.z();
        final Material material = AgentRequestParsers.parseMaterial(materialKey);
        if (material == null)
        {
            return AgentResponses.errorResponse(
                command.requestId(),
                AgentErrorCode.INVALID_ARGUMENT,
                "Unknown material '%s'.".formatted(materialKey)
            );
        }

        final String finalMaterial = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final World world = player.getWorld();
            world.getBlockAt(x, y, z).setType(material);
            return world.getBlockAt(x, y, z).getType().getKey().toString();
        });

        return AgentResponses.successResponse(command.requestId(), Map.of("material", finalMaterial));
    }

    /**
     * Handles {@code LEFT_CLICK_BLOCK} by firing a synthetic left-click block interaction event.
     *
     * @param command
     *     Typed command carrying UUID, block coordinates, and block face.
     * @return
     *     Success response containing whether the fired interaction event was cancelled.
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    AgentResponse handleLeftClickBlock(LeftClickBlockCommand command)
        throws Exception
    {
        return handleClickBlock(command.requestId(), command.uuid(),
            command.x(), command.y(), command.z(), command.blockFace(), Action.LEFT_CLICK_BLOCK);
    }

    /**
     * Handles {@code RIGHT_CLICK_BLOCK} by firing a synthetic right-click block interaction event.
     *
     * @param command
     *     Typed command carrying UUID, block coordinates, and block face.
     * @return
     *     Success response containing whether the fired interaction event was cancelled.
     * @throws Exception
     *     Propagates validation and main-thread execution failures.
     */
    AgentResponse handleRightClickBlock(RightClickBlockCommand command)
        throws Exception
    {
        return handleClickBlock(command.requestId(), command.uuid(),
            command.x(), command.y(), command.z(), command.blockFace(), Action.RIGHT_CLICK_BLOCK);
    }

    /**
     * Handles {@code GET_PLAYER_MESSAGES} by draining adapter messages and returning full tracked history.
     *
     * @param command
     *     Typed command carrying the player UUID.
     * @return
     *     Success response with {@code messagesJson}.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleGetPlayerMessages(GetPlayerMessagesCommand command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String messagesJson = mainThreadExecutor.callOnMainThread(() ->
        {
            playerStore.getRequiredPlayer(uuid);
            playerStore.capturePlayerMessages(botPlayerNmsAdapter, uuid);
            return objectMapper.writeValueAsString(playerStore.getPlayerMessages(uuid));
        });

        return AgentResponses.successResponse(command.requestId(), Map.of("messagesJson", messagesJson));
    }

    /**
     * Handles {@code GET_PLAYER_CHAT_COMPONENTS} by returning the serialized chat component history.
     *
     * @param command
     *     Typed command carrying the player UUID.
     * @return
     *     Success response with {@code componentsJson}.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleGetPlayerChatComponents(GetPlayerChatComponentsCommand command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String componentsJson = mainThreadExecutor.callOnMainThread(() ->
        {
            playerStore.getRequiredPlayer(uuid);
            return objectMapper.writeValueAsString(List.of());
        });

        return AgentResponses.successResponse(command.requestId(), Map.of("componentsJson", componentsJson));
    }

    /**
     * Handles {@code GET_PLAYER_CHAT_COMPONENTS} by draining adapter components and returning history.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; requires {@code uuid}.
     * @return
     *     Success response with {@code componentsJson}.
     * @throws Exception
     *     Propagates parsing and main-thread execution failures.
     */
    AgentResponse handleGetPlayerChatComponents(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final String componentsJson = mainThreadExecutor.callOnMainThread(() ->
        {
            playerStore.getRequiredPlayer(uuid);
            playerStore.capturePlayerChatComponents(botPlayerNmsAdapter, uuid);
            return objectMapper.writeValueAsString(playerStore.getPlayerChatComponents(uuid));
        });

        return AgentResponses.successResponse(requestId, Map.of("componentsJson", componentsJson));
    }

    /**
     * Handles {@code GET_PLAYER_INVENTORY} by returning a snapshot of the player's inventory.
     *
     * @param command
     *     Typed command carrying the player UUID.
     * @return
     *     Success response with {@code inventoryJson}.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleGetPlayerInventory(GetPlayerInventoryCommand command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String inventoryJson = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            return objectMapper.writeValueAsString(buildInventoryItems(player.getInventory().getContents()));
        });

        return AgentResponses.successResponse(command.requestId(), Map.of("inventoryJson", inventoryJson));
    }

    /**
     * Handles {@code DROP_ITEM} by simulating the player dropping their main hand item.
     *
     * @param command
     *     Typed command carrying the player UUID.
     * @return
     *     Success response with {@code dropped=true} when no plugin cancelled the event, {@code false} otherwise.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleDropItem(DropItemCommand command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final Boolean dropped = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || AgentMaterials.isAir(item.getType()))
                return Boolean.FALSE;

            final org.bukkit.entity.Item droppedItem =
                player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
            final org.bukkit.event.player.PlayerDropItemEvent event =
                new org.bukkit.event.player.PlayerDropItemEvent(player, droppedItem);

            Bukkit.getPluginManager().callEvent(event);
            droppedItem.remove();

            return !event.isCancelled();
        });

        return AgentResponses.successResponse(command.requestId(), Map.of("dropped", dropped.toString()));
    }

    /**
     * Handles {@code TELEPORT_PLAYER} by teleporting a synthetic player to the given world coordinates.
     *
     * @param command
     *     Typed command carrying player UUID, world name, and target coordinates.
     * @return
     *     Success response with {@code teleported} result, or error when the world does not exist.
     * @throws Exception
     *     Propagates main-thread execution failures.
     */
    AgentResponse handleTeleportPlayer(TeleportPlayerCommand command)
        throws Exception
    {
        final UUID uuid = command.uuid();
        final String worldName = command.worldName();
        final double x = command.x();
        final double y = command.y();
        final double z = command.z();

        if (worldName.isBlank())
        {
            return AgentResponses.errorResponse(
                command.requestId(),
                AgentErrorCode.INVALID_ARGUMENT,
                "Argument 'worldName' must not be blank."
            );
        }

        final Boolean teleported = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            return player.teleport(new Location(world, x, y, z));
        });

        return AgentResponses.successResponse(command.requestId(), Map.of("teleported", teleported.toString()));
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

    private AgentResponse handleClickBlock(
        String requestId,
        UUID uuid,
        int x,
        int y,
        int z,
        String blockFaceName,
        Action action)
        throws Exception
    {
        final BlockFace blockFace = AgentRequestParsers.parseBlockFace(blockFaceName);
        if (blockFace == null)
        {
            return AgentResponses.errorResponse(
                requestId,
                AgentErrorCode.INVALID_ARGUMENT,
                "Unknown block face '%s'.".formatted(blockFaceName)
            );
        }

        final Boolean cancelled = mainThreadExecutor.callOnMainThread(() ->
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

        return AgentResponses.successResponse(requestId, Map.of("cancelled", cancelled.toString()));
    }

    private static List<Map<String, Object>> buildInventoryItems(ItemStack... contents)
    {
        final List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < contents.length; i++)
        {
            final ItemStack item = contents[i];
            if (item == null || AgentMaterials.isAir(item.getType()))
                continue;

            final Map<String, Object> itemData = new HashMap<>();
            itemData.put("slot", i);
            itemData.put("materialKey", item.getType().getKey().toString());
            final String displayName = item.getItemMeta() == null ? null : item.getItemMeta().getDisplayName();
            itemData.put("displayName", displayName);
            itemData.put(
                "lore",
                item.getItemMeta() == null
                    ? List.of()
                    : Objects.requireNonNullElse(item.getItemMeta().getLore(), List.of())
            );
            items.add(itemData);
        }
        return items;
    }
}
