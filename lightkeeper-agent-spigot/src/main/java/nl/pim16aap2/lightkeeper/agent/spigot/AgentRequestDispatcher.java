package nl.pim16aap2.lightkeeper.agent.spigot;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.BlockType;
import nl.pim16aap2.lightkeeper.protocol.ClearCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.ClickMenuSlot;
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
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
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
import org.bukkit.Bukkit;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Central request router for the Spigot agent protocol.
 *
 * <p>This class parses request lines into protocol objects, enforces handshake preconditions, dispatches actions
 * to domain handlers, and maps failures into canonical error response JSON strings.
 */
final class AgentRequestDispatcher
{
    /**
     * JSON mapper used to deserialize raw request lines and serialize responses.
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
     *     Request JSON deserializer and response serializer.
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
     *     Dispatch result containing response JSON and updated handshake state.
     */
    RequestDispatchResult handleRequestLine(String line, boolean handshakeCompleted)
    {
        // Pre-extract requestId so validation failures still return a correlated error response.
        String requestId = "unknown";
        try
        {
            final JsonNode tree = objectMapper.readTree(line);
            requestId = tree.path("requestId").asString("unknown");
            @SuppressWarnings("rawtypes")
            final IAgentCommand command = objectMapper.treeToValue(tree, IAgentCommand.class);
            return dispatchCommand(command, handshakeCompleted);
        }
        catch (Exception exception)
        {
            return buildErrorResult(
                requestId,
                AgentErrorCode.INVALID_REQUEST,
                "Failed to parse request: " + exception.getMessage(),
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
     *     Dispatch result containing response JSON and updated handshake state.
     */
    @SuppressWarnings("rawtypes")
    private RequestDispatchResult dispatchCommand(IAgentCommand command, boolean handshakeCompleted)
    {
        final String requestId = command.requestId();

        try
        {
            if (command instanceof Handshake.Command hc)
            {
                final Handshake.Response handshakeResponse = handleHandshake(hc);
                return new RequestDispatchResult(
                    AgentResponses.successJson(objectMapper, handshakeResponse),
                    true
                );
            }

            if (!handshakeCompleted)
            {
                return buildErrorResult(
                    requestId,
                    AgentErrorCode.HANDSHAKE_REQUIRED,
                    "A successful HANDSHAKE action is required before '%s'."
                        .formatted(command.getClass().getSimpleName()),
                    false
                );
            }

            final IAgentResponse response = switch (command)
            {
                case MainWorld.Command c -> worldActions.handleMainWorld(c);
                case NewWorld.Command c -> worldActions.handleNewWorld(c);
                case ExecuteCommand.Command c -> worldActions.handleExecuteCommand(c);
                case BlockType.Command c -> worldActions.handleBlockType(c);
                case SetBlock.Command c -> worldActions.handleSetBlock(c);
                case CreatePlayer.Command c -> playerActions.handleCreatePlayer(c);
                case RemovePlayer.Command c -> playerActions.handleRemovePlayer(c);
                case ExecutePlayerCommand.Command c -> playerActions.handleExecutePlayerCommand(c);
                case PlacePlayerBlock.Command c -> playerActions.handlePlacePlayerBlock(c);
                case LeftClickBlock.Command c -> playerActions.handleLeftClickBlock(c);
                case RightClickBlock.Command c -> playerActions.handleRightClickBlock(c);
                case GetOpenMenu.Command c -> menuActions.handleGetOpenMenu(c);
                case ClickMenuSlot.Command c -> menuActions.handleClickMenuSlot(c);
                case DragMenuSlots.Command c -> menuActions.handleDragMenuSlots(c);
                case GetPlayerMessages.Command c -> playerActions.handleGetPlayerMessages(c);
                case WaitTicks.Command c -> worldActions.handleWaitTicks(c);
                case GetServerTick.Command c -> worldActions.handleGetServerTick(c);
                case TeleportPlayer.Command c -> playerActions.handleTeleportPlayer(c);
                case LoadChunk.Command c -> worldActions.handleLoadChunk(c);
                case UnloadChunk.Command c -> worldActions.handleUnloadChunk(c);
                case IsChunkLoaded.Command c -> worldActions.handleIsChunkLoaded(c);
                case GetPlayerInventory.Command c -> playerActions.handleGetPlayerInventory(c);
                case DropItem.Command c -> playerActions.handleDropItem(c);
                case RegisterEventListener.Command c -> playerActions.handleRegisterEventListener(c);
                case GetCapturedEvents.Command c -> playerActions.handleGetCapturedEvents(c);
                case ClearCapturedEvents.Command c -> playerActions.handleClearCapturedEvents(c);
                case UnregisterEventListener.Command c -> playerActions.handleUnregisterEventListener(c);
                case GetPlayerChatComponents.Command c -> playerActions.handleGetPlayerChatComponents(c);
                case GetServerPlatform.Command c -> worldActions.handleGetServerPlatform(c);
                case Handshake.Command ignored ->
                    throw new IllegalStateException("Unreachable HANDSHAKE dispatch branch.");
            };

            return new RequestDispatchResult(AgentResponses.successJson(objectMapper, response), true);
        }
        catch (AgentProtocolException exception)
        {
            return buildErrorResult(
                requestId,
                exception.errorCode(),
                Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName()),
                handshakeCompleted);
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

            return buildErrorResult(
                requestId,
                AgentErrorCode.REQUEST_FAILED,
                Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName()),
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
     *     Success response when validation succeeds.
     * @throws AgentProtocolException
     *     When token, protocol version, or agent SHA validation fails.
     */
    private Handshake.Response handleHandshake(Handshake.Command command)
    {
        final String token = command.token();
        final int clientProtocolVersion = command.protocolVersion();
        final String clientAgentSha = command.agentSha256();

        if (!authToken.equals(token))
            throw new AgentProtocolException(AgentErrorCode.AUTH_FAILED, "Auth token mismatch.");

        if (protocolVersion != clientProtocolVersion)
            throw new AgentProtocolException(
                AgentErrorCode.PROTOCOL_MISMATCH,
                "Runtime protocol version mismatch. expected=%d actual=%d."
                    .formatted(protocolVersion, clientProtocolVersion)
            );

        if (!expectedAgentSha256.isBlank() && !expectedAgentSha256.equalsIgnoreCase(clientAgentSha))
            throw new AgentProtocolException(AgentErrorCode.AGENT_SHA_MISMATCH, "Agent SHA-256 mismatch.");

        return new Handshake.Response(command.requestId(), protocolVersion, Bukkit.getBukkitVersion());
    }

    /**
     * Builds an error dispatch result, swallowing any secondary serialization failure with a fallback.
     *
     * @param requestId
     *     Correlated request identifier.
     * @param errorCode
     *     Error code to include in the response.
     * @param message
     *     Human-readable error message.
     * @param handshakeCompleted
     *     Current handshake state to propagate.
     * @return
     *     Error dispatch result.
     */
    private RequestDispatchResult buildErrorResult(
        String requestId,
        AgentErrorCode errorCode,
        String message,
        boolean handshakeCompleted)
    {
        try
        {
            return new RequestDispatchResult(
                AgentResponses.errorJson(objectMapper, requestId, errorCode, message),
                handshakeCompleted
            );
        }
        catch (JacksonException serializationException)
        {
            logger.log(Level.SEVERE, "Failed to serialize error response.", serializationException);
            final String fallback =
                ("{\"requestId\":\"%s\",\"success\":false,\"errorCode\":\"%s\","
                    + "\"errorMessage\":\"serialization failure\"}")
                    .formatted(requestId, errorCode.wireCode());
            return new RequestDispatchResult(fallback, handshakeCompleted);
        }
    }

    /**
     * Immutable transport object describing a dispatched response JSON and resulting handshake state.
     *
     * @param responseJson
     *     Protocol response JSON string generated for the processed request.
     * @param handshakeCompleted
     *     Whether handshake is completed after processing the request.
     */
    record RequestDispatchResult(String responseJson, boolean handshakeCompleted)
    {
    }
}
