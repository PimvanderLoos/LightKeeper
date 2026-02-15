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

final class AgentPlayerActions
{
    private final JavaPlugin plugin;
    private final AgentMainThreadExecutor mainThreadExecutor;
    private final AgentSyntheticPlayerStore playerStore;
    private final AgentMenuActions menuActions;
    private final ObjectMapper objectMapper;
    private final IBotPlayerNmsAdapter botPlayerNmsAdapter;

    AgentPlayerActions(
        JavaPlugin plugin,
        AgentMainThreadExecutor mainThreadExecutor,
        AgentSyntheticPlayerStore playerStore,
        AgentMenuActions menuActions,
        ObjectMapper objectMapper,
        IBotPlayerNmsAdapter botPlayerNmsAdapter)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.playerStore = Objects.requireNonNull(playerStore, "playerStore");
        this.menuActions = Objects.requireNonNull(menuActions, "menuActions");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.botPlayerNmsAdapter = Objects.requireNonNull(botPlayerNmsAdapter, "botPlayerNmsAdapter");
    }

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
            if (command.equalsIgnoreCase("lktestgui") || command.equalsIgnoreCase("lightkeeper:testgui"))
            {
                menuActions.openMainMenu(player);
                return true;
            }

            boolean result = player.performCommand(command);
            if (!result)
                result = Bukkit.dispatchCommand(player, command);
            return result;
        });

        return AgentResponses.successResponse(requestId, Map.of("success", success.toString()));
    }

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
