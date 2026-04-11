package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentErrorCode;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Central request router for the Spigot agent protocol.
 *
 * <p>This class parses request lines into protocol objects, enforces handshake preconditions, dispatches actions
 * to domain handlers, and maps failures into canonical error responses.
 */
final class AgentRequestDispatcher
{
    /**
     * JSON mapper used to deserialize raw request lines.
     */
    private final ObjectMapper objectMapper;
    /**
     * Handler for world and server-level protocol actions.
     */
    private final AgentWorldActions worldActions;
    /**
     * Handler for synthetic player and player-context protocol actions.
     */
    private final AgentPlayerActions playerActions;
    /**
     * Handler for inventory/menu protocol actions.
     */
    private final AgentMenuActions menuActions;
    /**
     * Plugin logger used for operational diagnostics.
     */
    private final java.util.logging.Logger logger;

    /**
     * Expected handshake token.
     */
    private final String authToken;
    /**
     * Expected runtime protocol version.
     */
    private final int protocolVersion;
    /**
     * Optional expected SHA-256 hash of the running agent artifact.
     */
    private final String expectedAgentSha256;

    /**
     * @param objectMapper
     *     Request JSON deserializer.
     * @param worldActions
     *     World action handler.
     * @param playerActions
     *     Player action handler.
     * @param menuActions
     *     Menu action handler.
     * @param logger
     *     Logger for request and error diagnostics.
     * @param authToken
     *     Expected handshake token.
     * @param protocolVersion
     *     Expected protocol version.
     * @param expectedAgentSha256
     *     Optional expected agent artifact hash.
     */
    AgentRequestDispatcher(
        ObjectMapper objectMapper,
        AgentWorldActions worldActions,
        AgentPlayerActions playerActions,
        AgentMenuActions menuActions,
        java.util.logging.Logger logger,
        String authToken,
        int protocolVersion,
        String expectedAgentSha256)
    {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.worldActions = Objects.requireNonNull(worldActions, "worldActions");
        this.playerActions = Objects.requireNonNull(playerActions, "playerActions");
        this.menuActions = Objects.requireNonNull(menuActions, "menuActions");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.authToken = Objects.requireNonNull(authToken, "authToken");
        this.protocolVersion = protocolVersion;
        this.expectedAgentSha256 = Objects.requireNonNull(expectedAgentSha256, "expectedAgentSha256");
    }

    /**
     * Performs best-effort cleanup of registered synthetic players.
     */
    void cleanupSyntheticPlayers()
    {
        playerActions.cleanupSyntheticPlayers();
    }

    /**
     * Parses and dispatches a single raw request line.
     *
     * @param line
     *     Raw JSON request line.
     * @param handshakeCompleted
     *     Whether the current connection has already completed handshake successfully.
     * @return
     *     Dispatch result containing response payload and updated handshake state.
     */
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
                    AgentErrorCode.INVALID_REQUEST,
                    "Failed to parse request: " + exception.getMessage()
                ),
                handshakeCompleted
            );
        }
    }

    /**
     * Dispatches a parsed request to the relevant action handler.
     *
     * @param request
     *     Parsed protocol request.
     * @param handshakeCompleted
     *     Current connection handshake state.
     * @return
     *     Dispatch result containing response payload and updated handshake state.
     */
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
                        AgentErrorCode.HANDSHAKE_REQUIRED,
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
                    AgentErrorCode.REQUEST_FAILED,
                    Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName())
                ),
                handshakeCompleted
            );
        }
    }

    /**
     * Validates handshake credentials and compatibility metadata.
     *
     * @param requestId
     *     Runtime request identifier.
     * @param arguments
     *     Handshake arguments containing token/protocol/hash values.
     * @return
     *     Success response when validation succeeds; otherwise a specific handshake error response.
     */
    private AgentResponse handleHandshake(String requestId, Map<String, String> arguments)
    {
        final String token = arguments.getOrDefault("token", "");
        final String rawClientProtocolVersion = arguments.getOrDefault("protocolVersion", "").trim();
        final String clientAgentSha = arguments.getOrDefault("agentSha256", "");

        if (!authToken.equals(token))
            return AgentResponses.errorResponse(requestId, AgentErrorCode.AUTH_FAILED, "Auth token mismatch.");

        final int clientProtocolVersion;
        try
        {
            clientProtocolVersion = Integer.parseInt(rawClientProtocolVersion);
        }
        catch (NumberFormatException exception)
        {
            return AgentResponses.errorResponse(
                requestId,
                AgentErrorCode.PROTOCOL_MISMATCH,
                "Runtime protocol version mismatch. expected=%d actual=%s."
                    .formatted(protocolVersion, rawClientProtocolVersion),
                Map.of(
                    "expectedProtocolVersion", Integer.toString(protocolVersion),
                    "actualProtocolVersion", rawClientProtocolVersion
                )
            );
        }

        if (protocolVersion != clientProtocolVersion)
        {
            return AgentResponses.errorResponse(
                requestId,
                AgentErrorCode.PROTOCOL_MISMATCH,
                "Runtime protocol version mismatch. expected=%d actual=%d."
                    .formatted(protocolVersion, clientProtocolVersion),
                Map.of(
                    "expectedProtocolVersion", Integer.toString(protocolVersion),
                    "actualProtocolVersion", Integer.toString(clientProtocolVersion)
                )
            );
        }

        if (!expectedAgentSha256.isBlank() && !expectedAgentSha256.equalsIgnoreCase(clientAgentSha))
            return AgentResponses.errorResponse(
                requestId,
                AgentErrorCode.AGENT_SHA_MISMATCH,
                "Agent SHA-256 mismatch."
            );

        return AgentResponses.successResponse(requestId, Map.of(
            "protocolVersion", Integer.toString(protocolVersion),
            "bukkitVersion", Bukkit.getBukkitVersion()
        ));
    }

    /**
     * Immutable transport object describing a dispatched response and resulting handshake state.
     *
     * @param response
     *     Protocol response generated for the processed request.
     * @param handshakeCompleted
     *     Whether handshake is completed after processing the request.
     */
    record RequestDispatchResult(AgentResponse response, boolean handshakeCompleted)
    {
    }
}
