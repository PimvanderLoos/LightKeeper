package nl.pim16aap2.lightkeeper.agent.spigot;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.ValueInstantiationException;
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
     * Handler for synthetic-player messages and inventory state.
     */
    private final AgentPlayerStateActions playerStateActions;
    /**
     * Handler for inventory/menu protocol actions.
     */
    private final AgentMenuActions menuActions;
    /**
     * Handler for dynamic Bukkit event capture.
     */
    private final AgentEventActions eventActions;

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
     * @param playerStateActions
     *     Player message and inventory-state handler.
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
        AgentPlayerStateActions playerStateActions,
        AgentMenuActions menuActions,
        AgentEventActions eventActions,
        Config config)
    {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.worldActions = Objects.requireNonNull(worldActions, "worldActions");
        this.playerActions = Objects.requireNonNull(playerActions, "playerActions");
        this.playerStateActions = Objects.requireNonNull(playerStateActions, "playerStateActions");
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
        catch (ValueInstantiationException exception)
        {
            // A record compact-constructor rejecting a bad field throws IllegalArgumentException during
            // deserialization; Jackson wraps it in ValueInstantiationException. Preserve the domain contract by
            // mapping that back to INVALID_ARGUMENT rather than the generic INVALID_REQUEST parse code.
            final Throwable cause = exception.getCause();
            if (cause instanceof IllegalArgumentException)
                return buildErrorResult(
                    requestId,
                    AgentErrorCode.INVALID_ARGUMENT,
                    Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getName()),
                    handshakeCompleted
                );
            return buildParseFailure(requestId, exception, handshakeCompleted);
        }
        catch (Exception exception)
        {
            return buildParseFailure(requestId, exception, handshakeCompleted);
        }
    }

    /**
     * Builds an {@code INVALID_REQUEST} result for a request line that could not be parsed.
     *
     * @param requestId
     *     Correlated request identifier (or {@code "unknown"} when it could not be extracted).
     * @param exception
     *     The parse failure.
     * @param handshakeCompleted
     *     Current handshake state to propagate.
     * @return Error dispatch result.
     */
    private RequestDispatchResult buildParseFailure(String requestId, Exception exception, boolean handshakeCompleted)
    {
        return buildErrorResult(
            requestId,
            AgentErrorCode.INVALID_REQUEST,
            "Failed to parse request: " + Objects.requireNonNullElse(exception.getMessage(),
                exception.getClass().getName()),
            handshakeCompleted
        );
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
                    AgentResponses.successJson(objectMapper, requestId, handshakeResponse, hc.responseType()),
                    true
                );
            }

            if (!handshakeCompleted)
            {
                return buildErrorResult(
                    requestId,
                    AgentErrorCode.HANDSHAKE_REQUIRED,
                    "A successful HANDSHAKE action is required before '%s'."
                        .formatted(qualifiedCommandName(command)),
                    false
                );
            }

            // Each arm goes through handle(...), which forces the handler's return type to be the command's
            // declared response type: a mispaired handler (e.g. handleSetBlock returning MainWorld.Response)
            // fails to compile rather than silently deserializing into the wrong record on the client.
            final IAgentResponse response = switch (command)
            {
                case MainWorld.Command c -> handle(c, worldActions::handleMainWorld);
                case NewWorld.Command c -> handle(c, worldActions::handleNewWorld);
                case ExecuteCommand.Command c -> handle(c, worldActions::handleExecuteCommand);
                case BlockType.Command c -> handle(c, worldActions::handleBlockType);
                case SetBlock.Command c -> handle(c, worldActions::handleSetBlock);
                case CreatePlayer.Command c -> handle(c, playerActions::handleCreatePlayer);
                case RemovePlayer.Command c -> handle(c, playerActions::handleRemovePlayer);
                case ExecutePlayerCommand.Command c -> handle(c, playerActions::handleExecutePlayerCommand);
                case PlacePlayerBlock.Command c -> handle(c, playerActions::handlePlacePlayerBlock);
                case LeftClickBlock.Command c -> handle(c, playerActions::handleLeftClickBlock);
                case RightClickBlock.Command c -> handle(c, playerActions::handleRightClickBlock);
                case GetOpenMenu.Command c -> handle(c, menuActions::handleGetOpenMenu);
                case ClickMenuSlot.Command c -> handle(c, menuActions::handleClickMenuSlot);
                case DragMenuSlots.Command c -> handle(c, menuActions::handleDragMenuSlots);
                case GetPlayerMessages.Command c -> handle(c, playerStateActions::handleGetPlayerMessages);
                case WaitTicks.Command c -> handle(c, worldActions::handleWaitTicks);
                case GetServerTick.Command c -> handle(c, worldActions::handleGetServerTick);
                case TeleportPlayer.Command c -> handle(c, playerActions::handleTeleportPlayer);
                case LoadChunk.Command c -> handle(c, worldActions::handleLoadChunk);
                case UnloadChunk.Command c -> handle(c, worldActions::handleUnloadChunk);
                case IsChunkLoaded.Command c -> handle(c, worldActions::handleIsChunkLoaded);
                case GetPlayerInventory.Command c -> handle(c, playerStateActions::handleGetPlayerInventory);
                case DropItem.Command c -> handle(c, playerStateActions::handleDropItem);
                case RegisterEventListener.Command c -> handle(c, eventActions::handleRegisterEventListener);
                case GetCapturedEvents.Command c -> handle(c, eventActions::handleGetCapturedEvents);
                case ClearCapturedEvents.Command c -> handle(c, eventActions::handleClearCapturedEvents);
                case UnregisterEventListener.Command c -> handle(c, eventActions::handleUnregisterEventListener);
                case GetPlayerChatComponents.Command c -> handle(c, playerStateActions::handleGetPlayerChatComponents);
                case GetServerPlatform.Command c -> handle(c, worldActions::handleGetServerPlatform);
                case Handshake.Command ignored ->
                    throw new IllegalStateException("Unreachable HANDSHAKE dispatch branch.");
            };

            return new RequestDispatchResult(
                AgentResponses.successJson(objectMapper, requestId, response, command.responseType()), true);
        }
        catch (AgentProtocolException exception)
        {
            final Throwable cause = exception.getCause();
            final String baseMessage =
                Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName());
            if (cause == null)
                return buildErrorResult(requestId, exception.errorCode(), baseMessage, handshakeCompleted);

            // A structured failure that wraps a cause (e.g. INTERRUPTED/TIMEOUT) otherwise loses the cause's
            // message and stack entirely: log it and append it to the wire message so it is not invisible.
            logger.log(
                Level.WARNING,
                "Agent action '%s' failed for request '%s' with code %s."
                    .formatted(qualifiedCommandName(command), requestId, exception.errorCode()),
                exception
            );
            return buildErrorResult(
                requestId,
                exception.errorCode(),
                baseMessage + " (cause: " + cause + ")",
                handshakeCompleted
            );
        }
        catch (IllegalArgumentException exception)
        {
            return buildErrorResult(
                requestId,
                AgentErrorCode.INVALID_ARGUMENT,
                Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName()),
                handshakeCompleted
            );
        }
        catch (VirtualMachineError error)
        {
            // Unrecoverable (OutOfMemoryError/StackOverflowError); never swallow it into a response.
            throw error;
        }
        catch (Throwable throwable)
        {
            // Catch Throwable, not just Exception: the NMS layer is reflection-heavy, so a NoClassDefFoundError
            // or LinkageError on a new server build would otherwise sail past every catch, kill the
            // per-connection thread, and leave the client with only "connection closed unexpectedly". Return a
            // coded response instead.
            logger.log(
                Level.SEVERE,
                "Agent action '%s' failed for request '%s': %s"
                    .formatted(
                        qualifiedCommandName(command),
                        requestId,
                        Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getName())
                    ),
                throwable
            );

            return buildErrorResult(
                requestId,
                AgentErrorCode.REQUEST_FAILED,
                Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getName()),
                handshakeCompleted
            );
        }
    }

    /**
     * Invokes a command handler, constraining at compile time that the handler returns the response type the
     * command declares via {@code IAgentCommand<R>}. A mispaired handler therefore fails to compile.
     *
     * @param command
     *     The parsed command to handle.
     * @param handler
     *     The domain handler for that command.
     * @param <C>
     *     The command type.
     * @param <R>
     *     The response type the command is paired with.
     * @return The handler's response.
     * @throws Exception
     *     Propagates handler failures.
     */
    private <C extends IAgentCommand<R>, R extends IAgentResponse> R handle(C command, CommandHandler<C, R> handler)
        throws Exception
    {
        return handler.handle(command);
    }

    /**
     * A command handler paired with its command's declared response type.
     *
     * @param <C>
     *     The command type.
     * @param <R>
     *     The response type the command is paired with.
     */
    @FunctionalInterface
    private interface CommandHandler<C extends IAgentCommand<R>, R extends IAgentResponse>
    {
        /**
         * Handles the command and returns its typed response.
         *
         * @param command
         *     The command to handle.
         * @return The typed response.
         * @throws Exception
         *     Propagates handler failures.
         */
        R handle(C command) throws Exception;
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

        return new Handshake.Response(protocolVersion, Bukkit.getBukkitVersion());
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
                    .formatted(jsonEscape(requestId), errorCode.wireCode());
            return new RequestDispatchResult(fallback, handshakeCompleted);
        }
    }

    /**
     * Returns a qualified name for a command for use in log messages and error strings.
     *
     * <p>For nested classes like {@code MainWorld.Command}, {@code getSimpleName()} alone returns {@code "Command"},
     * which is indistinguishable across all command types. This method combines the enclosing class name to produce
     * {@code "MainWorld.Command"}.
     */
    @SuppressWarnings("rawtypes")
    private static String qualifiedCommandName(IAgentCommand command)
    {
        final Class<?> cls = command.getClass();
        final Class<?> enclosing = cls.getEnclosingClass();
        return enclosing != null
            ? enclosing.getSimpleName() + "." + cls.getSimpleName()
            : cls.getSimpleName();
    }

    /**
     * Escapes a string for safe embedding in a manually constructed JSON value.
     *
     * <p>Only called on the fallback error path when Jackson serialization itself has failed.
     */
    private static String jsonEscape(String value)
    {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
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
}
