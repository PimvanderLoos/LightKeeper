package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments. Requires {@code name} and {@code worldName}; optional spawn/health/permission fields.
     * @return
     *     Success response containing the created player's UUID and name, or a validation error response.
     * @throws Exception
     *     Propagates parsing, validation, and main-thread execution failures.
     */
    AgentResponse handleCreatePlayer(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String name = arguments.getOrDefault("name", "").trim();
        final String worldName = arguments.getOrDefault("worldName", "").trim();
        if (name.isBlank())
            return AgentResponses.errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'name' must not be blank.");
        if (worldName.isBlank())
        {
            return AgentResponses.errorResponse(
                requestId,
                "INVALID_ARGUMENT",
                "Argument 'worldName' must not be blank."
            );
        }

        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", UUID.randomUUID().toString()));
        final Double x = AgentRequestParsers.parseOptionalDouble(arguments.get("x"));
        final Double y = AgentRequestParsers.parseOptionalDouble(arguments.get("y"));
        final Double z = AgentRequestParsers.parseOptionalDouble(arguments.get("z"));
        final Double health = AgentRequestParsers.parseOptionalDouble(arguments.get("health"));
        final String permissionsCsv = arguments.getOrDefault("permissions", "");

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

            if (!permissionsCsv.isBlank())
                playerStore.setPermissions(plugin, uuid, spawnedPlayer, permissionsCsv);

            return spawnedPlayer;
        });

        playerStore.registerSyntheticPlayer(uuid, player);

        plugin.getLogger().info(
            "LK_AGENT: Created synthetic player '%s' (%s) in world '%s'."
                .formatted(player.getName(), player.getUniqueId(), worldName)
        );
        return AgentResponses.successResponse(requestId, Map.of(
            "uuid", player.getUniqueId().toString(),
            "name", player.getName()
        ));
    }

    /**
     * Handles {@code REMOVE_PLAYER} by removing permissions, despawning the synthetic player, and cleaning state.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; requires {@code uuid}.
     * @return
     *     Success response when cleanup completes.
     * @throws Exception
     *     Propagates parsing and main-thread execution failures.
     */
    AgentResponse handleRemovePlayer(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
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

        return AgentResponses.successResponse(requestId, Map.of("removed", "true"));
    }

    /**
     * Handles {@code EXECUTE_PLAYER_COMMAND} by dispatching the command in the player's execution context.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; requires {@code uuid} and non-blank {@code command}.
     * @return
     *     Success response containing whether dispatch succeeded.
     * @throws Exception
     *     Propagates parsing and main-thread execution failures.
     */
    AgentResponse handleExecutePlayerCommand(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final String rawCommand = arguments.getOrDefault("command", "").trim();
        if (rawCommand.isBlank())
        {
            return AgentResponses.errorResponse(
                requestId,
                "INVALID_ARGUMENT",
                "Argument 'command' must not be blank."
            );
        }

        final String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean success = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            boolean result = player.performCommand(command);
            if (!result)
                result = Bukkit.dispatchCommand(player, command);
            return result;
        });

        return AgentResponses.successResponse(requestId, Map.of("success", success.toString()));
    }

    /**
     * Handles {@code PLACE_PLAYER_BLOCK} by setting the target block type in the player's current world.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; requires {@code uuid}, block coordinates, and {@code material}.
     * @return
     *     Success response containing the resulting block material key.
     * @throws Exception
     *     Propagates parsing, validation, and main-thread execution failures.
     */
    AgentResponse handlePlacePlayerBlock(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final String materialName = arguments.getOrDefault("material", "");
        final int x = AgentRequestParsers.parseInt(arguments.getOrDefault("x", "0"));
        final int y = AgentRequestParsers.parseInt(arguments.getOrDefault("y", "0"));
        final int z = AgentRequestParsers.parseInt(arguments.getOrDefault("z", "0"));
        final Material material = AgentRequestParsers.parseMaterial(materialName);
        if (material == null)
        {
            return AgentResponses.errorResponse(
                requestId,
                "INVALID_ARGUMENT",
                "Unknown material '%s'.".formatted(materialName)
            );
        }

        final String finalMaterial = mainThreadExecutor.callOnMainThread(() ->
        {
            final Player player = playerStore.getRequiredPlayer(uuid);
            final World world = player.getWorld();
            world.getBlockAt(x, y, z).setType(material);
            return world.getBlockAt(x, y, z).getType().getKey().toString();
        });

        return AgentResponses.successResponse(requestId, Map.of("material", finalMaterial));
    }

    /**
     * Handles {@code GET_PLAYER_MESSAGES} by draining adapter messages and returning full tracked history.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Request arguments; requires {@code uuid}.
     * @return
     *     Success response with {@code messagesJson}.
     * @throws Exception
     *     Propagates parsing and main-thread execution failures.
     */
    AgentResponse handleGetPlayerMessages(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final String messagesJson = mainThreadExecutor.callOnMainThread(() ->
        {
            playerStore.getRequiredPlayer(uuid);
            playerStore.capturePlayerMessages(botPlayerNmsAdapter, uuid);
            return objectMapper.writeValueAsString(playerStore.getPlayerMessages(uuid));
        });

        return AgentResponses.successResponse(requestId, Map.of("messagesJson", messagesJson));
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
}
