package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

final class AgentRequestDispatcher
{
    private final ObjectMapper objectMapper;
    private final AgentWorldActions worldActions;
    private final AgentPlayerActions playerActions;
    private final AgentMenuActions menuActions;
    private final java.util.logging.Logger logger;

    private final String authToken;
    private final String protocolVersion;
    private final String expectedAgentSha256;

    AgentRequestDispatcher(
        ObjectMapper objectMapper,
        AgentWorldActions worldActions,
        AgentPlayerActions playerActions,
        AgentMenuActions menuActions,
        java.util.logging.Logger logger,
        String authToken,
        String protocolVersion,
        String expectedAgentSha256)
    {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.worldActions = Objects.requireNonNull(worldActions, "worldActions");
        this.playerActions = Objects.requireNonNull(playerActions, "playerActions");
        this.menuActions = Objects.requireNonNull(menuActions, "menuActions");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.authToken = Objects.requireNonNull(authToken, "authToken");
        this.protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion");
        this.expectedAgentSha256 = Objects.requireNonNull(expectedAgentSha256, "expectedAgentSha256");
    }

    void openMainMenu(Player player)
    {
        menuActions.openMainMenu(player);
    }

    void onInventoryClick(InventoryClickEvent event)
    {
        menuActions.onInventoryClick(event);
    }

    void cleanupSyntheticPlayers()
    {
        playerActions.cleanupSyntheticPlayers();
    }

    RequestDispatchResult handleRequestLine(String line, boolean handshakeCompleted)
    {
        try
        {
            final AgentRequest request = objectMapper.readValue(line, AgentRequest.class);
            return dispatchRequest(request, handshakeCompleted);
        }
        catch (Exception exception)
        {
            return new RequestDispatchResult(
                AgentResponses.errorResponse(
                    "unknown",
                    "INVALID_REQUEST",
                    "Failed to parse request: " + exception.getMessage()
                ),
                handshakeCompleted
            );
        }
    }

    private RequestDispatchResult dispatchRequest(AgentRequest request, boolean handshakeCompleted)
    {
        final Map<String, String> arguments = request.arguments();
        final String requestId = request.requestId();

        try
        {
            if (request.action() == AgentAction.HANDSHAKE)
            {
                final AgentResponse handshakeResponse = handleHandshake(requestId, arguments);
                return new RequestDispatchResult(handshakeResponse, handshakeCompleted || handshakeResponse.success());
            }

            if (!handshakeCompleted)
            {
                return new RequestDispatchResult(
                    AgentResponses.errorResponse(
                        requestId,
                        "HANDSHAKE_REQUIRED",
                        "A successful HANDSHAKE action is required before '%s'."
                            .formatted(String.valueOf(request.action()))
                    ),
                    false
                );
            }

            return new RequestDispatchResult(switch (request.action())
            {
                case MAIN_WORLD -> worldActions.handleMainWorld(requestId);
                case NEW_WORLD -> worldActions.handleNewWorld(requestId, arguments);
                case EXECUTE_COMMAND -> worldActions.handleExecuteCommand(requestId, arguments);
                case BLOCK_TYPE -> worldActions.handleBlockType(requestId, arguments);
                case SET_BLOCK -> worldActions.handleSetBlock(requestId, arguments);
                case CREATE_PLAYER -> playerActions.handleCreatePlayer(requestId, arguments);
                case REMOVE_PLAYER -> playerActions.handleRemovePlayer(requestId, arguments);
                case EXECUTE_PLAYER_COMMAND -> playerActions.handleExecutePlayerCommand(requestId, arguments);
                case PLACE_PLAYER_BLOCK -> playerActions.handlePlacePlayerBlock(requestId, arguments);
                case GET_OPEN_MENU -> menuActions.handleGetOpenMenu(requestId, arguments);
                case CLICK_MENU_SLOT -> menuActions.handleClickMenuSlot(requestId, arguments);
                case DRAG_MENU_SLOTS -> menuActions.handleDragMenuSlots(requestId, arguments);
                case GET_PLAYER_MESSAGES -> playerActions.handleGetPlayerMessages(requestId, arguments);
                case WAIT_TICKS -> worldActions.handleWaitTicks(requestId, arguments);
                case GET_SERVER_TICK -> worldActions.handleGetServerTick(requestId);
                case HANDSHAKE -> throw new IllegalStateException("Unreachable HANDSHAKE dispatch branch.");
            }, true);
        }
        catch (Exception exception)
        {
            logger.log(
                Level.SEVERE,
                "Agent action '%s' failed for request '%s': %s"
                    .formatted(
                        request.action(),
                        requestId,
                        Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName())
                    ),
                exception
            );

            return new RequestDispatchResult(
                AgentResponses.errorResponse(
                    requestId,
                    "REQUEST_FAILED",
                    Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName())
                ),
                handshakeCompleted
            );
        }
    }

    private AgentResponse handleHandshake(String requestId, Map<String, String> arguments)
    {
        final String token = arguments.getOrDefault("token", "");
        final String clientProtocolVersion = arguments.getOrDefault("protocolVersion", "");
        final String clientAgentSha = arguments.getOrDefault("agentSha256", "");

        if (!authToken.equals(token))
            return AgentResponses.errorResponse(requestId, "AUTH_FAILED", "Auth token mismatch.");
        if (!protocolVersion.equals(clientProtocolVersion))
        {
            return AgentResponses.errorResponse(
                requestId,
                "PROTOCOL_MISMATCH",
                "Runtime protocol version mismatch."
            );
        }
        if (!expectedAgentSha256.isBlank() && !expectedAgentSha256.equalsIgnoreCase(clientAgentSha))
            return AgentResponses.errorResponse(requestId, "AGENT_SHA_MISMATCH", "Agent SHA-256 mismatch.");

        return AgentResponses.successResponse(requestId, Map.of(
            "protocolVersion", protocolVersion,
            "bukkitVersion", Bukkit.getBukkitVersion()
        ));
    }

    record RequestDispatchResult(AgentResponse response, boolean handshakeCompleted)
    {
    }
}
