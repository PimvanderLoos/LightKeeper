package nl.pim16aap2.lightkeeper.agent.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.IAgentCommand;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentResponse;
import nl.pim16aap2.lightkeeper.protocol.BlockTypeCommand;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEventsCommand;
import nl.pim16aap2.lightkeeper.protocol.ClickMenuSlotCommand;
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
     * Handler for dynamic Bukkit event capture.
     */
    private final AgentEventActions eventActions;
    /**
     * Immutable configuration block for the dispatcher.
     *
     * @param authToken
     *     Expected handshake token.
     * @param protocolVersion
     *     Expected runtime protocol version.
     * @param expectedAgentSha256
     *     Optional expected agent artifact SHA-256 hash; blank disables the check.
     * @param logger
     *     Logger for request and error diagnostics.
     */
    record Config(String authToken, int protocolVersion, String expectedAgentSha256, java.util.logging.Logger logger)
    {
        Config
        {
            Objects.requireNonNull(authToken, "authToken");
            Objects.requireNonNull(expectedAgentSha256, "expectedAgentSha256");
            Objects.requireNonNull(logger, "logger");
        }
    }

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
     * @param eventActions
     *     Event capture action handler.
     * @param config
     *     Immutable dispatcher configuration (auth, protocol, SHA, logger).
     */
    AgentRequestDispatcher(
        ObjectMapper objectMapper,
        AgentWorldActions worldActions,
        AgentPlayerActions playerActions,
        AgentMenuActions menuActions,
        AgentEventActions eventActions,
        Config config)
    {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.worldActions = Objects.requireNonNull(worldActions, "worldActions");
        this.playerActions = Objects.requireNonNull(playerActions, "playerActions");
        this.menuActions = Objects.requireNonNull(menuActions, "menuActions");
        this.eventActions = Objects.requireNonNull(eventActions, "eventActions");
        Objects.requireNonNull(config, "config");
        this.logger = config.logger();
        this.authToken = config.authToken();
        this.protocolVersion = config.protocolVersion();
        this.expectedAgentSha256 = config.expectedAgentSha256();
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
            final IAgentCommand command = objectMapper.readValue(line, IAgentCommand.class);
            return dispatchCommand(command, handshakeCompleted);
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
     * Dispatches a parsed command to the relevant action handler.
     *
     * @param command
     *     Parsed protocol command.
     * @param handshakeCompleted
     *     Current connection handshake state.
     * @return
     *     Dispatch result containing response payload and updated handshake state.
     */
    private RequestDispatchResult dispatchCommand(IAgentCommand command, boolean handshakeCompleted)
    {
        final String requestId = command.requestId();

        try
        {
            if (command instanceof HandshakeCommand hc)
            {
                final AgentResponse handshakeResponse = handleHandshake(hc);
                return new RequestDispatchResult(handshakeResponse, handshakeCompleted || handshakeResponse.success());
            }

            if (!handshakeCompleted)
            {
                return new RequestDispatchResult(
                    AgentResponses.errorResponse(
                        requestId,
                        AgentErrorCode.HANDSHAKE_REQUIRED,
                        "A successful HANDSHAKE action is required before '%s'."
                            .formatted(command.getClass().getSimpleName())
                    ),
                    false
                );
            }

            return new RequestDispatchResult(switch (command)
            {
                case MainWorldCommand c -> worldActions.handleMainWorld(c);
                case NewWorldCommand c -> worldActions.handleNewWorld(c);
                case ExecuteCommandCommand c -> worldActions.handleExecuteCommand(c);
                case BlockTypeCommand c -> worldActions.handleBlockType(c);
                case SetBlockCommand c -> worldActions.handleSetBlock(c);
                case CreatePlayerCommand c -> playerActions.handleCreatePlayer(c);
                case RemovePlayerCommand c -> playerActions.handleRemovePlayer(c);
                case ExecutePlayerCommandCommand c -> playerActions.handleExecutePlayerCommand(c);
                case PlacePlayerBlockCommand c -> playerActions.handlePlacePlayerBlock(c);
                case LeftClickBlockCommand c -> playerActions.handleLeftClickBlock(c);
                case RightClickBlockCommand c -> playerActions.handleRightClickBlock(c);
                case GetOpenMenuCommand c -> menuActions.handleGetOpenMenu(c);
                case ClickMenuSlotCommand c -> menuActions.handleClickMenuSlot(c);
                case DragMenuSlotsCommand c -> menuActions.handleDragMenuSlots(c);
                case GetPlayerMessagesCommand c -> playerActions.handleGetPlayerMessages(c);
                case WaitTicksCommand c -> worldActions.handleWaitTicks(c);
                case GetServerTickCommand c -> worldActions.handleGetServerTick(c);
                case TeleportPlayerCommand c -> playerActions.handleTeleportPlayer(c);
                case LoadChunkCommand c -> worldActions.handleLoadChunk(c);
                case UnloadChunkCommand c -> worldActions.handleUnloadChunk(c);
                case IsChunkLoadedCommand c -> worldActions.handleIsChunkLoaded(c);
                case GetPlayerInventoryCommand c -> playerActions.handleGetPlayerInventory(c);
                case DropItemCommand c -> playerActions.handleDropItem(c);
                case RegisterEventListenerCommand c -> eventActions.handleRegisterEventListener(c);
                case GetCapturedEventsCommand c -> eventActions.handleGetCapturedEvents(c);
                case ClearCapturedEventsCommand c -> eventActions.handleClearCapturedEvents(c);
                case UnregisterEventListenerCommand c -> eventActions.handleUnregisterEventListener(c);
                case GetPlayerChatComponentsCommand c -> playerActions.handleGetPlayerChatComponents(c);
                case GetServerPlatformCommand c -> worldActions.handleGetServerPlatform(c);
                case HandshakeCommand ignored ->
                    throw new IllegalStateException("Unreachable HANDSHAKE dispatch branch.");
            }, true);
        }
        catch (IllegalArgumentException exception)
        {
            return new RequestDispatchResult(
                AgentResponses.errorResponse(
                    requestId,
                    AgentErrorCode.INVALID_ARGUMENT,
                    Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName())
                ),
                handshakeCompleted
            );
        }
        catch (Exception exception)
        {
            logger.log(
                Level.SEVERE,
                "Agent action '%s' failed for request '%s': %s"
                    .formatted(
                        command.getClass().getSimpleName(),
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
     * @param command
     *     Handshake command containing token, protocol version, and agent hash.
     * @return
     *     Success response when validation succeeds; otherwise a specific handshake error response.
     */
    private AgentResponse handleHandshake(HandshakeCommand command)
    {
        final String requestId = command.requestId();
        final String token = command.token();
        final int clientProtocolVersion = command.protocolVersion();
        final String clientAgentSha = command.agentSha256();

        if (!authToken.equals(token))
            return AgentResponses.errorResponse(requestId, AgentErrorCode.AUTH_FAILED, "Auth token mismatch.");

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
